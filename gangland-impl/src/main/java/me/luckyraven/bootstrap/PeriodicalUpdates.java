package me.luckyraven.bootstrap;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.plugin.PluginData;
import me.luckyraven.data.plugin.PluginDataCleanupService;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.TableLookup;
import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.TimeUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@CustomLog
public final class PeriodicalUpdates implements BeanLifecycle {

	private final Gangland                   gangland;
	private final GanglandDatabase           database;
	private final DatabaseHelper             helper;
	private final RepositoryRegistry         repositoryRegistry;
	private final PluginManager              pluginManager;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final WeaponManager              weaponManager;

	@Getter
	private PluginDataCleanupService cleanupService;
	private RepeatingTimer           repeatingTimer;

	public PeriodicalUpdates(Gangland gangland,
	                         GanglandDatabase database,
	                         PluginManager pluginManager,
	                         UserManager<Player> userManager,
	                         UserManager<OfflinePlayer> offlineUserManager,
	                         WeaponManager weaponManager,
	                         long interval) {
		this(gangland, database, pluginManager, userManager, offlineUserManager, weaponManager);
		this.repeatingTimer = new RepeatingTimer(gangland, 20L * interval, timer -> task());
	}

	public PeriodicalUpdates(Gangland gangland,
	                         GanglandDatabase database,
	                         PluginManager pluginManager,
	                         UserManager<Player> userManager,
	                         UserManager<OfflinePlayer> offlineUserManager,
	                         WeaponManager weaponManager) {
		this.gangland           = gangland;
		this.database           = database;
		this.pluginManager      = pluginManager;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.weaponManager      = weaponManager;
		this.helper             = new DatabaseHelper(gangland, database);
		this.repositoryRegistry = database.getRepositoryRegistry();
	}

	/**
	 * All queried data is sent and handled in the database.
	 * <p>
	 * User and bank data (online and offline) are saved directly via table queries so that the offline user cache can
	 * be cleared immediately after. Everything else is persisted through each manager's data supplier via
	 * {@link RepositoryRegistry#saveAll()}.
	 */
	public void updatingDatabase() {
		updatingDatabase(null);
	}

	/**
	 * Same as {@link #updatingDatabase()} but invokes {@code onComplete} after all repository saves finish (including
	 * async ones).
	 *
	 * @param onComplete called when all saves are done, or {@code null} for fire-and-forget
	 */
	public void updatingDatabase(Runnable onComplete) {
		List<Table<?>> tables = database.getTables();

		// adjust plugin scan dates before the repository save
		for (PluginData pluginData : pluginManager.getPluginDataList()) {
			adjustScheduledScanDate(pluginData);
		}

		// save user and bank data - kept as direct table updates so the offline cache can be
		// cleared synchronously after saving
		UserTable userTable = TableLookup.find(UserTable.class, tables);
		BankTable bankTable = TableLookup.find(BankTable.class, tables);

		// online users
		Collection<User<Player>> onlineUsers = userManager.getUsers().values();
		Collection<Bank> onlineBanks = userManager.getUsers().values()
				.stream().filter(User::hasBank).map(User::getBank).toList();
		updateAllData(userTable, onlineUsers);
		updateAllData(bankTable, onlineBanks);

		// offline users
		Collection<User<OfflinePlayer>> offlineUsers = offlineUserManager.getUsers().values();
		Collection<Bank> offlineBanks = offlineUserManager.getUsers().values()
				.stream().filter(User::hasBank).map(User::getBank).toList();
		updateAllData(userTable, offlineUsers);
		updateAllData(bankTable, offlineBanks);
		offlineUserManager.clear();

		// update all repositories (rank, permissions, gangs, alliances, members, waypoints,
		//                          weapons, loot chests, plugin data, cop spawners, jails, detainment)
		repositoryRegistry.saveAll(onComplete);
	}

	/**
	 * Resets the cache data.
	 */
	public void resetCache() { }

	/**
	 * Updates the plugin information.
	 */
	public void forceUpdate() {
		log.info("Force update...");
		task();
	}

	/**
	 * Stops the periodical update timer.
	 */
	public void stop() {
		if (this.repeatingTimer == null) return;

		this.repeatingTimer.stop();
		this.repeatingTimer = null;
	}

	// ----------------------------------------------------------------------------------------------------------------
	// BeanLifecycle — reload and shutdown
	// ----------------------------------------------------------------------------------------------------------------

	/**
	 * Stops the repeating timer before data is cleared during a reload pass.
	 */
	@Override
	public void onPreClear() {
		stop();
	}

	/**
	 * Re-reads the auto-save interval from {@link Settings} (which may have changed during the file reload pass),
	 * recreates the repeating timer, and starts it.
	 */
	@Override
	public void onInitialize(boolean firstLoad) {
		if (Settings.isAutoSave()) {
			long interval = Settings.getAutoSaveTime() * 60L;
			this.repeatingTimer = new RepeatingTimer(gangland, 20L * interval, timer -> task());
		}
		initializeCleanupService();
		start();
	}

	/**
	 * Stops the timer on plugin disable. Does <b>not</b> call {@link #forceUpdate()} — the final force-save is handled
	 * explicitly by {@code Gangland.onDisable()} after all beans have shut down, so that converted records from other
	 * beans (e.g. CarService active sessions → parked records) are included in the save.
	 */
	@Override
	public void onShutdown() {
		stop();
	}

	/**
	 * Starts the periodical update tasks.
	 */
	public void start() {
		if (this.repeatingTimer == null) return;

		log.info("Initializing auto-save...");

		initializeCleanupService();

		this.repeatingTimer.start(true);
	}

	private void initializeCleanupService() {
		var weaponRepository = database.getRepositoryRegistry().getRepository(Weapon.class);
		cleanupService = new PluginDataCleanupService(pluginManager, weaponRepository, weaponManager);
	}

	private void task() {
		long    start    = System.currentTimeMillis();
		boolean logDebug = Settings.isAutoSaveDebug();

		// Check for scheduled cleanup
		if (cleanupService != null) {
			try {
				cleanupService.checkAndPerformCleanup();
			} catch (Throwable throwable) {
				log.error("There was an issue during cleanup check...", throwable);
			}
		}

		// resetting player inventories
		if (logDebug) log.info("Cache reset...");
		try {
			resetCache();
		} catch (Throwable exception) {
			log.error("There was an issue resetting the cache...", exception);
		}

		// auto-saving - timing log fires in the callback after all async saves complete
		if (logDebug) log.info("Saving...");
		try {
			updatingDatabase(() -> {
				if (!logDebug) return;

				log.info("Data save complete");
				processTime(start);
			});
		} catch (Throwable throwable) {
			log.error("There was an issue saving the data...");
			if (logDebug) processTime(start);
		}
	}

	private void processTime(long start) {
		log.info("The process took {}ms", System.currentTimeMillis() - start);
	}

	private <T> void updateAllData(Table<T> table, Collection<? extends T> collection) {
		helper.runQueries(database -> table.batchUpsertTableQuery(database, new ArrayList<>(collection)));
	}

	/**
	 * Adjusts the scheduled scan date if the current scheduled time has passed or needs recalculation based on the last
	 * scan date.
	 */
	private void adjustScheduledScanDate(PluginData pluginData) {
		long now = System.currentTimeMillis();

		// If scheduled time has already passed, let the cleanup service handle it
		if (now >= pluginData.getScheduledScanDate()) return;

		// Calculate what the scheduled date SHOULD be based on last scan and current config
		long lastScanDate          = pluginData.getScanDate();
		long expectedScheduledDate = TimeUtil.addDays(lastScanDate, Settings.getCleanUpTime());

		// Only adjust if the config has changed (expected != stored)
		if (expectedScheduledDate == pluginData.getScheduledScanDate()) return;

		pluginData.setScheduledScanDate(expectedScheduledDate);

		if (Settings.isAutoSaveDebug()) {
			log.info("Cleanup interval config changed. Adjusted scheduled scan date to: {}",
			         new Date(expectedScheduledDate));
		}
	}

}
