package me.luckyraven;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Date;

@Getter
public final class PluginData {

	@Setter
	private static int ID = 0;

	@Getter(value = AccessLevel.NONE)
	private final JavaPlugin plugin;
	private final int        id;
	private final Date       pluginActivateDate;
	private final Date       scanDate;
	private final Date       scheduledScanDate;

	public PluginData(JavaPlugin plugin, int id, Date pluginActivateDate, Date scanDate, Date scheduledScanDate) {
		this.id                 = id;
		this.plugin             = plugin;
		this.pluginActivateDate = pluginActivateDate;
		this.scanDate           = scanDate;
		this.scheduledScanDate  = scheduledScanDate;
	}

	private int getNewId() {
		return ID++;
	}
}
