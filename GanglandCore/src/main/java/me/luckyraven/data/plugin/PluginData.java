package me.luckyraven.data.plugin;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
public final class PluginData {

	@Setter
	private static int ID = 0;

	private final int  id;
	private final Date pluginActivateDate;
	private final Date scanDate;
	private final Date scheduledScanDate;

	public PluginData(int id, Date pluginActivateDate, Date scanDate, Date scheduledScanDate) {
		this.id                 = id;
		this.pluginActivateDate = pluginActivateDate;
		this.scanDate           = scanDate;
		this.scheduledScanDate  = scheduledScanDate;
	}

	public PluginData(Date pluginActivateDate, Date scanDate, Date scheduledScanDate) {
		this(getNewId(), pluginActivateDate, scanDate, scheduledScanDate);
	}

	private static int getNewId() {
		return ID++;
	}
}
