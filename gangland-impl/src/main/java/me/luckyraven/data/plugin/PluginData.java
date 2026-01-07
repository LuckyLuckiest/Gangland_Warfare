package me.luckyraven.data.plugin;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Getter
public final class PluginData {

	@Setter
	private static int ID = 0;

	private final int  id;
	// the day the plugin was activated
	private final long pluginActivateDate;
	// the scan date of the plugin according to the previous value
	@Setter
	private       long scanDate;
	// the next scan date of the plugin
	@Setter
	private       long scheduledScanDate;

	public PluginData(int id, long pluginActivateDate, long scanDate, long scheduledScanDate) {
		this.id                 = id;
		this.pluginActivateDate = pluginActivateDate;
		this.scanDate           = scanDate;
		this.scheduledScanDate  = scheduledScanDate;
	}

	public PluginData(long pluginActivateDate, long scanDate, long scheduledScanDate) {
		this(getNewId(), pluginActivateDate, scanDate, scheduledScanDate);
	}

	/**
	 * Creates initial plugin data with the activation date set to now, and schedules the first scan based on the given
	 * interval.
	 *
	 * @param scanIntervalDays days until the first scheduled scan
	 *
	 * @return new PluginData instance
	 */
	public static PluginData createInitial(int scanIntervalDays) {
		long now           = Instant.now().toEpochMilli();
		long scheduledScan = Instant.now().plus(scanIntervalDays, ChronoUnit.DAYS).toEpochMilli();
		return new PluginData(now, 0L, scheduledScan);
	}

	private static int getNewId() {
		return ID++;
	}

	public Date getEquivalentDate(long epochSecond) {
		return new Date(epochSecond);
	}

	/**
	 * Checks if it's time to perform a scheduled scan.
	 *
	 * @return true if current time is past or equal to scheduledScanDate
	 */
	public boolean isScanDue() {
		return System.currentTimeMillis() >= scheduledScanDate;
	}

	/**
	 * Updates scan dates after a successful scan.
	 *
	 * @param nextScanIntervalDays days until the next scan
	 */
	public void updateAfterScan(int nextScanIntervalDays) {
		this.scanDate          = System.currentTimeMillis();
		this.scheduledScanDate = Instant.now().plus(nextScanIntervalDays, ChronoUnit.DAYS).toEpochMilli();
	}

}
