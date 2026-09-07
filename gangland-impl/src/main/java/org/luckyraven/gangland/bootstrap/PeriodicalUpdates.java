package org.luckyraven.gangland.bootstrap;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.timer.RepeatingTimer;
import org.luckyraven.keystone.util.TimeUtil;
import org.luckyraven.gangland.data.plugin.PluginData;
import org.luckyraven.gangland.data.plugin.PluginDataCleanupService;
import org.luckyraven.gangland.data.plugin.PluginManager;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponManager;

import java.util.Date;

@CustomLog
public final class PeriodicalUpdates implements BeanLifecycle {

	private final Gangland           gangland;
	private final GanglandDatabase   database;
	private final RepositoryRegistry repositoryRegistry;
	private final PluginManager      pluginManager;
	/**
	 * Kept as a field even though the save now runs entirely through the repository data suppliers: the online
	 * manager is a constructor dependency that pins bean ordering, and the offline cache still has to be cleared
	 * here after its snapshot has been taken.
	 */
	@SuppressWarnings("unused")
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
		this.repositoryRegistry = database.getRepositoryRegistry();
	}

	/**
	 * All queried data is sent and handled in the database.
	 * <p>
	 * Everything — users and banks included — is persisted through each manager's data supplier via
	 * {@link RepositoryRegistry#saveAll()}. The offline user cache is cleared afterwards, once {@code saveAll} has
	 * taken its snapshot.
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
		// adjust plugin scan dates before the repository save
		for (PluginData pluginData : pluginManager.getPluginDataList()) {
			adjustScheduledScanDate(pluginData);
		}

		// Update every repository — users and banks included. Both UserManager beans are linked (see
		// DataConfig.offlineUserManager), so the UserRepository / BankRepository data suppliers now cover the online
		// AND the offline cache; the separate direct table writes this method used to make were an exact duplicate
		// of that work and were removed rather than left to race the repository writes on another thread.
		//
		// saveAll() reads every supplier and copies it on THIS thread (AbstractRepository.saveAll snapshots before
		// its async hop), so it must be called on the main thread — see start() — and the offline cache may only be
		// cleared after it returns.
		repositoryRegistry.saveAll(onComplete);

		// the offline cache is a write-through buffer for players who already left: once its snapshot is on its way
		// to the database there is nothing left to keep
		offlineUserManager.clear();
	}

	/**
	 * Resets the cache data.
	 */
	public void resetCache() { }

	/**
	 * Updates the plugin information.
	 */
	public void forceUpdate() {
		forceUpdate(null);
	}

	/**
	 * Same as {@link #forceUpdate()} but invokes {@code onComplete} after every async repository save has finished. The
	 * callback may fire on an async thread — hop back to the main thread via the Bukkit scheduler if the work needs to
	 * touch Bukkit state.
	 *
	 * @param onComplete called once all saves are done, or {@code null} for fire-and-forget
	 */
	public void forceUpdate(Runnable onComplete) {
		log.info("Force update...");
		task(onComplete);
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
	 *
	 * <p>The timer is deliberately <b>synchronous</b> (CL-02): {@link #updatingDatabase(Runnable)} clears the offline
	 * user cache and every repository data supplier copies its manager's live map. Those maps are mutated on the main
	 * thread by join / quit handling, so iterating them off-thread raced into
	 * {@code ConcurrentModificationException}s (a partial save that skipped the remaining repositories) and lost
	 * writes. Every snapshot is therefore taken on the main thread; only the JDBC work leaves it, through
	 * {@code AbstractRepository}'s own async hop inside {@code RepositoryRegistry.saveAll}.
	 */
	public void start() {
		if (this.repeatingTimer == null) return;

		log.info("Initializing auto-save...");

		initializeCleanupService();

		this.repeatingTimer.start(false);
	}

	private void initializeCleanupService() {
		var weaponRepository = database.getRepositoryRegistry().getRepository(Weapon.class);
		cleanupService = new PluginDataCleanupService(pluginManager, weaponRepository, weaponManager);
	}

	private void task() {
		task(null);
	}

	private void task(Runnable onComplete) {
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
				if (logDebug) {
					log.info("Data save complete");
					processTime(start);
				}
				if (onComplete != null) onComplete.run();
			});
		} catch (Throwable throwable) {
			log.error("There was an issue saving the data...");
			if (logDebug) processTime(start);
			if (onComplete != null) onComplete.run();
		}
	}

	private void processTime(long start) {
		log.info("The process took {}ms", System.currentTimeMillis() - start);
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
