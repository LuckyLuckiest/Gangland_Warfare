package org.luckyraven.gangland.data.plugin;

import lombok.CustomLog;
import org.luckyraven.keystone.util.TimeUtil;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.TimeMessages;

import java.util.Date;
import java.util.List;

/**
 * Service responsible for cleaning up unused data from the database based on the plugin's scheduled scan dates.
 */
@CustomLog
public final class PluginDataCleanupService {

	private final boolean logDebug = Settings.isAutoSaveDebug();

	private final PluginManager pluginManager;

	public PluginDataCleanupService(PluginManager pluginManager) {
		this.pluginManager = pluginManager;
	}

	/**
	 * Checks if a cleanup scan is due and performs it if necessary.
	 */
	public void checkAndPerformCleanup() {
		if (validatePluginData()) return;

		List<PluginData> dataList   = pluginManager.getPluginDataList();
		PluginData       pluginData = dataList.get(dataList.size() - 1);
		long             now        = System.currentTimeMillis();

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

		List<PluginData> dataList = pluginManager.getPluginDataList();
		performCleanup(dataList.get(dataList.size() - 1));
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

		// Update plugin data with new scan dates (will be persisted by PeriodicalUpdates)
		long now          = System.currentTimeMillis();
		Date nextScanDate = pluginManager.nextPlannedDate(new Date(now));

		pluginData.setScanDate(now);
		pluginData.setScheduledScanDate(nextScanDate.getTime());

		if (logDebug) log.info("Cleanup completed. Next scan scheduled for: {}", nextScanDate);

		long duration = System.currentTimeMillis() - startTime;
		if (logDebug) log.info("Cleanup scan completed in {}ms", duration);
	}

}
