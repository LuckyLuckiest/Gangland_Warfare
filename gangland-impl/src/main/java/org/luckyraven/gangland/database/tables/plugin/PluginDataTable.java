package org.luckyraven.gangland.database.tables.plugin;

import org.luckyraven.gangland.data.plugin.PluginData;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class PluginDataTable extends Table<PluginData> {

	public PluginDataTable() {
		super("plugin_data");

		Attribute<Integer> id                 = new Attribute<>("id", true, Integer.class);
		Attribute<Long>    pluginActivateDate = new Attribute<>("activate_date", false, Long.class);
		Attribute<Long>    scanDate           = new Attribute<>("scan_date", false, Long.class);
		Attribute<Long>    scheduledScanDate  = new Attribute<>("scheduled_scan_date", false, Long.class);

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
