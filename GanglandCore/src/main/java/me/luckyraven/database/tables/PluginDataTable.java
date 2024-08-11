package me.luckyraven.database.tables;

import me.luckyraven.data.plugin.PluginData;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Date;
import java.util.Map;

public class PluginDataTable extends Table<PluginData> {

	public PluginDataTable() {
		super("plugin_data");

		Attribute<Integer> id                 = new Attribute<>("id", true, Integer.class);
		Attribute<Date>    pluginActivateDate = new Attribute<>("activate_date", false, Date.class);
		Attribute<Date>    scanDate           = new Attribute<>("scan_date", false, Date.class);
		Attribute<Date>    scheduledScanDate  = new Attribute<>("scheduled_scan_date", false, Date.class);

		this.addAttribute(id);
		this.addAttribute(pluginActivateDate);
		this.addAttribute(scanDate);
		this.addAttribute(scheduledScanDate);
	}

	@Override
	public Object[] getData(PluginData data) {
		return new Object[]{data.getId(), data.getPluginActivateDate(), data.getScanDate(),
							data.getScheduledScanDate()};
	}

	@Override
	public Map<String, Object> searchCriteria(PluginData data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
