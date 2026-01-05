package me.luckyraven.data.plugin;

import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.WeaponTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.Date;

/**
 * Service responsible for cleaning up unused data from the database based on the plugin's scheduled scan dates.
 */
public class PluginDataCleanupService {

	private static final Logger logger = LogManager.getLogger(PluginDataCleanupService.class.getSimpleName());

	private final PluginManager  pluginManager;
	private final DatabaseHelper databaseHelper;
	private final WeaponTable    weaponTable;

	public PluginDataCleanupService(PluginManager pluginManager, DatabaseHelper databaseHelper,
									WeaponTable weaponTable) {
		this.pluginManager  = pluginManager;
		this.databaseHelper = databaseHelper;
		this.weaponTable    = weaponTable;
	}

	/**
	 * Checks if a cleanup scan is due and performs it if necessary.
	 */
	public void checkAndPerformCleanup() {
		if (pluginManager.getPluginDataList().isEmpty()) {
			logger.warn("Plugin data not initialized.");
			return;
		}

		PluginData pluginData = pluginManager.getPluginDataList().getFirst();
		long       now        = System.currentTimeMillis();

		if (now >= pluginData.getScheduledScanDate()) {
			logger.info("Scheduled cleanup scan is due. Starting cleanup...");
			performCleanup(pluginData);
			return;
		}

		long timeUntilScan  = pluginData.getScheduledScanDate() - now;
		long hoursUntilScan = timeUntilScan / (1000 * 60 * 60);
		logger.debug("Next cleanup scan in approximately {} hours", hoursUntilScan);
	}

	/**
	 * Forces an immediate cleanup regardless of the scheduled time.
	 */
	public void forceCleanup() {
		if (pluginManager.getPluginDataList().isEmpty()) {
			logger.warn("Plugin data not initialized.");
			return;
		}

		logger.info("Forcing immediate cleanup scan...");
		performCleanup(pluginManager.getPluginDataList().getFirst());
	}

	private void performCleanup(PluginData pluginData) {
		long startTime = System.currentTimeMillis();

		databaseHelper.runQueries(database -> {
			// Reset weapons in the database
			int weaponsReset = resetWeapons(database);
			logger.info("Reset {} weapons from database", weaponsReset);
		});

		// Update plugin data with new scan dates (will be persisted by PeriodicalUpdates)
		long now          = System.currentTimeMillis();
		Date nextScanDate = pluginManager.nextPlannedDate(new Date(now));

		pluginData.setScanDate(now);
		pluginData.setScheduledScanDate(nextScanDate.getTime());

		logger.info("Cleanup completed. Next scan scheduled for: {}", nextScanDate);

		long duration = System.currentTimeMillis() - startTime;
		logger.info("Cleanup scan completed in {}ms", duration);
	}

	private int resetWeapons(Database database) throws SQLException {
		int totalBefore = database.table(weaponTable.getName()).totalRows();

		database.table(weaponTable.getName()).delete("1", "1");

		logger.debug("Cleared {} weapons from weapon table", totalBefore);
		return totalBefore;
	}

}