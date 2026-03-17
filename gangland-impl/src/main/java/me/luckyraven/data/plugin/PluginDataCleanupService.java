package me.luckyraven.data.plugin;

import lombok.CustomLog;
import me.luckyraven.database.repositories.weapon.WeaponRepository;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.utilities.TimeUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;

import java.util.Date;

/**
 * Service responsible for cleaning up unused data from the database based on the plugin's scheduled scan dates.
 */
@CustomLog
public final class PluginDataCleanupService {

	private final boolean logDebug = Settings.isAutoSaveDebug();

	private final PluginManager       pluginManager;
	private final IRepository<Weapon> weaponRepository;
	private final WeaponManager       weaponManager;

	public PluginDataCleanupService(PluginManager pluginManager, IRepository<Weapon> weaponRepository,
									WeaponManager weaponManager) {
		this.pluginManager    = pluginManager;
		this.weaponRepository = weaponRepository;
		this.weaponManager    = weaponManager;
	}

	/**
	 * Checks if a cleanup scan is due and performs it if necessary.
	 */
	public void checkAndPerformCleanup() {
		if (validatePluginData()) return;

		PluginData pluginData = pluginManager.getPluginDataList().getLast();
		long       now        = System.currentTimeMillis();

		if (now >= pluginData.getScheduledScanDate()) {
			if (logDebug) log.info("Scheduled cleanup scan is due. Starting cleanup...");
			performCleanup(pluginData);
			return;
		}

		long   timeUntilScanMillis  = pluginData.getScheduledScanDate() - now;
		long   timeUntilScanSeconds = Math.max(0, timeUntilScanMillis / 1000);
		String expectedValue        = TimeUtil.formatTime(timeUntilScanSeconds, true, TimeMessages.getInstance());

		if (logDebug) log.info("Next cleanup scan in approximately {}.", expectedValue);
	}

	/**
	 * Forces an immediate cleanup regardless of the scheduled time.
	 */
	public void forceCleanup() {
		if (validatePluginData()) return;

		if (logDebug) log.info("Forcing immediate cleanup scan...");

		performCleanup(pluginManager.getPluginDataList().getLast());
	}

	private boolean validatePluginData() {
		if (pluginManager.getPluginDataList().isEmpty()) {
			log.warn("Plugin data not initialized.");
			return true;
		}

		return false;
	}

	private void performCleanup(PluginData pluginData) {
		long startTime = System.currentTimeMillis();

		// Reset weapons in the database
		int weaponsReset = resetWeapons();
		if (logDebug) log.info("Reset {} weapons from database", weaponsReset);

		// Update plugin data with new scan dates (will be persisted by PeriodicalUpdates)
		long now          = System.currentTimeMillis();
		Date nextScanDate = pluginManager.nextPlannedDate(new Date(now));

		pluginData.setScanDate(now);
		pluginData.setScheduledScanDate(nextScanDate.getTime());

		if (logDebug) log.info("Cleanup completed. Next scan scheduled for: {}", nextScanDate);

		long duration = System.currentTimeMillis() - startTime;
		if (logDebug) log.info("Cleanup scan completed in {}ms", duration);
	}

	private int resetWeapons() {
		int count = weaponManager.getWeapons().size();

		if (weaponRepository instanceof WeaponRepository repo) {
			repo.deleteAll();
		}

		weaponManager.clear();

		if (logDebug) log.info("Cleared {} weapons from weapon table", count);
		return count;
	}

}
