package me.luckyraven;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.data.account.Bank;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.plugin.PluginData;
import me.luckyraven.data.plugin.PluginDataCleanupService;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.database.tables.weapon.WeaponTable;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.TimeUtil;
import me.luckyraven.weapon.WeaponManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@CustomLog
public final class PeriodicalUpdates {

	private final Gangland           gangland;
	private final Initializer        initializer;
	private final GanglandDatabase   database;
	private final DatabaseHelper     helper;
	private final RepositoryRegistry repositoryRegistry;

	@Getter
	private PluginDataCleanupService cleanupService;
	private RepeatingTimer           repeatingTimer;

	public PeriodicalUpdates(Gangland gangland, long interval) {
		this(gangland);
		this.repeatingTimer = new RepeatingTimer(gangland, 20L * interval, timer -> task());
	}

	public PeriodicalUpdates(Gangland gangland) {
		this.gangland           = gangland;
		this.initializer        = gangland.getInitializer();
		this.database           = initializer.getGanglandDatabase();
		this.helper             = new DatabaseHelper(gangland, database);
		this.repositoryRegistry = initializer.getGanglandDatabase().getRepositoryRegistry();
	}

	/**
	 * All queried data is sent and handled in the database.
	 * <p>
	 * User and bank data (online and offline) are saved directly via table queries so that the offline user cache can
	 * be cleared immediately after. Everything else is persisted through each manager's data supplier via
	 * {@link RepositoryRegistry#saveAll()}.
	 */
	public void updatingDatabase() {
		List<Table<?>> tables = database.getTables();

		// adjust plugin scan dates before the repository save
		PluginManager pluginManager = initializer.getPluginManager();

		for (PluginData pluginData : pluginManager.getPluginDataList()) {
			adjustScheduledScanDate(pluginData);
		}

		// save user and bank data — kept as direct table updates so the offline cache can be
		// cleared synchronously after saving
		UserManager<Player>        userManager        = initializer.getUserManager();
		UserManager<OfflinePlayer> offlineUserManager = initializer.getOfflineUserManager();
		UserTable                  userTable          = initializer.getInstanceFromTables(UserTable.class, tables);
		BankTable                  bankTable          = initializer.getInstanceFromTables(BankTable.class, tables);

		// online users
		Collection<User<Player>> onlineUsers = userManager.getUsers().values();
		Collection<Bank> onlineBanks = userManager.getUsers().values()
				.stream().map(User::getBank).toList();
		updateAllData(userTable, onlineUsers, null);
		updateAllData(bankTable, onlineBanks, null);

		// offline users
		Collection<User<OfflinePlayer>> offlineUsers = offlineUserManager.getUsers().values();
		Collection<Bank> offlineBanks = offlineUserManager.getUsers().values()
				.stream().map(User::getBank).toList();
		updateAllData(userTable, offlineUsers, null);
		updateAllData(bankTable, offlineBanks, null);
		offlineUserManager.clear();

		// update all repositories (rank, permissions, gangs, alliances, members, waypoints,
		//                          weapons, loot chests, plugin data, cop spawners, jails, detainment)
		repositoryRegistry.saveAll();
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
		GanglandDatabase database = initializer.getGanglandDatabase();
		DatabaseHelper   helper   = new DatabaseHelper(gangland, database);
		List<Table<?>>   tables   = database.getTables();

		PluginManager pluginManager = initializer.getPluginManager();
		WeaponTable   weaponTable   = initializer.getInstanceFromTables(WeaponTable.class, tables);
		WeaponManager weaponManager = initializer.getWeaponManager();

		cleanupService = new PluginDataCleanupService(pluginManager, helper, weaponTable, weaponManager);
	}

	private void task() {
		long    start    = System.currentTimeMillis();
		boolean logDebug = SettingAddon.isAutoSaveDebug();

		// Check for scheduled cleanup
		if (cleanupService != null) {
			try {
				cleanupService.checkAndPerformCleanup();
			} catch (Throwable throwable) {
				log.error("There was an issue during cleanup check...", throwable);
			}
		}

		// auto-saving
		if (logDebug) log.info("Saving...");
		try {
			updatingDatabase();
			if (logDebug) log.info("Data save complete");
		} catch (Throwable throwable) {
			log.error("There was an issue saving the data...");
		}

		// resetting player inventories
		if (logDebug) log.info("Cache reset...");
		try {
			resetCache();
		} catch (Throwable exception) {
			log.error("There was an issue resetting the cache...", exception);
		}

		long end = System.currentTimeMillis();

		if (logDebug) log.info("The process took {}ms", end - start);
	}

	private <T> void updateAllData(Table<T> table, Collection<? extends T> collection, @Nullable Consumer<T> consumer) {
		helper.runQueries(database -> {
			for (T row : collection) {
				if (consumer != null) consumer.accept(row);

				Map<String, Object> search = table.searchCriteria(row);
				Object[] data = database.table(table.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) {
					table.insertTableQuery(database, row);
				} else {
					table.updateTableQuery(database, row);
				}
			}
		});
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
		long expectedScheduledDate = TimeUtil.addDays(lastScanDate, SettingAddon.getCleanUpTime());

		// Only adjust if the config has changed (expected != stored)
		if (expectedScheduledDate == pluginData.getScheduledScanDate()) return;

		pluginData.setScheduledScanDate(expectedScheduledDate);

		if (SettingAddon.isAutoSaveDebug()) {
			log.info("Cleanup interval config changed. Adjusted scheduled scan date to: {}",
					 new Date(expectedScheduledDate));
		}
	}

}
