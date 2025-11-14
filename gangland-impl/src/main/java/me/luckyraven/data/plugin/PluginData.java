package me.luckyraven.data.plugin;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
public final class PluginData {

	@Setter
	private static int ID = 0;

	private final int  id;
	// the day the plugin was activated
	private final long pluginActivateDate;
	// the scan date of the plugin according to the previous value
	private final long scanDate;
	// the next scan date of the plugin
	private final long scheduledScanDate;

	public PluginData(int id, long pluginActivateDate, long scanDate, long scheduledScanDate) {
		this.id                 = id;
		this.pluginActivateDate = pluginActivateDate;
		this.scanDate           = scanDate;
		this.scheduledScanDate  = scheduledScanDate;
	}

	public PluginData(long pluginActivateDate, long scanDate, long scheduledScanDate) {
		this(getNewId(), pluginActivateDate, scanDate, scheduledScanDate);
	}

	private static int getNewId() {
		return ID++;
	}

	public Date getEquivalentDate(long epochSecond) {
		return new Date(epochSecond);
	}

}
