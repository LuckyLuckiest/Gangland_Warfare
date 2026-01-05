package me.luckyraven.data.plugin;

import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.WeaponTable;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.TimeUtil;
import me.luckyraven.weapon.WeaponManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;

/**
 * Service responsible for cleaning up unused data from the database based on the plugin's scheduled scan dates.
 */
public final class PluginDataCleanupService {

	private static final Logger logger = LogManager.getLogger(PluginDataCleanupService.class.getSimpleName());

	private final boolean log = SettingAddon.isAutoSaveDebug();

	private final PluginManager  pluginManager;
	private final DatabaseHelper databaseHelper;
	private final WeaponTable    weaponTable;
	private final WeaponManager  weaponManager;

	public PluginDataCleanupService(PluginManager pluginManager, DatabaseHelper databaseHelper, WeaponTable weaponTable,
									WeaponManager weaponManager) {
		this.pluginManager  = pluginManager;
		this.databaseHelper = databaseHelper;
		this.weaponTable    = weaponTable;
		this.weaponManager  = weaponManager;
	}

	/**
	 * Checks if a cleanup scan is due and performs it if necessary.
	 */
	public void checkAndPerformCleanup() {
		if (validatePluginData()) return;

		PluginData pluginData = pluginManager.getPluginDataList().getLast();
		long       now        = System.currentTimeMillis();

		if (now >= pluginData.getScheduledScanDate()) {
			if (log) logger.info("Scheduled cleanup scan is due. Starting cleanup...");
			performCleanup(pluginData);
			return;
		}

		long   timeUntilScanMillis  = pluginData.getScheduledScanDate() - now;
		long   timeUntilScanSeconds = Math.max(0, timeUntilScanMillis / 1000);
		String expectedValue        = TimeUtil.formatTime(timeUntilScanSeconds, true);

		if (log) logger.info("Next cleanup scan in approximately {}.", expectedValue);
	}

	/**
	 * Forces an immediate cleanup regardless of the scheduled time.
	 */
	public void forceCleanup() {
		if (validatePluginData()) return;

		if (log) logger.info("Forcing immediate cleanup scan...");

		performCleanup(pluginManager.getPluginDataList().getLast());
	}

	private boolean validatePluginData() {
		if (pluginManager.getPluginDataList().isEmpty()) {
			logger.warn("Plugin data not initialized.");
			return true;
		}

		return false;
	}

	private void performCleanup(PluginData pluginData) {
		long startTime = System.currentTimeMillis();

		databaseHelper.runQueries(database -> {
			// Reset weapons in the database
			int weaponsReset = resetWeapons(database);
			if (log) logger.info("Reset {} weapons from database", weaponsReset);
		});

		// Update plugin data with new scan dates (will be persisted by PeriodicalUpdates)
		long now          = System.currentTimeMillis();
		Date nextScanDate = pluginManager.nextPlannedDate(new Date(now));

		pluginData.setScanDate(now);
		pluginData.setScheduledScanDate(nextScanDate.getTime());

		if (log) logger.info("Cleanup completed. Next scan scheduled for: {}", nextScanDate);

		long duration = System.currentTimeMillis() - startTime;
		if (log) logger.info("Cleanup scan completed in {}ms", duration);
	}

	private int resetWeapons(Database database) throws SQLException {
		int totalBefore = database.table(weaponTable.getName()).totalRows();

		// Delete all rows by passing empty column - this triggers DELETE without WHERE
		database.table(weaponTable.getName()).delete("", null, Types.NULL);
		weaponManager.clear();

		if (log) logger.info("Cleared {} weapons from weapon table", totalBefore);
		return totalBefore;
	}

}