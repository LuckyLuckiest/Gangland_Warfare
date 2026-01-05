package me.luckyraven.data.plugin;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.PluginDataTable;
import me.luckyraven.file.configuration.SettingAddon;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PluginManager {

	private final Gangland         gangland;
	private final List<PluginData> pluginDataList;

	public PluginManager(Gangland gangland) {
		this.gangland       = gangland;
		this.pluginDataList = new ArrayList<>();
	}

	public void initialize(PluginDataTable table) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			List<Object[]> data = database.table(table.getName()).selectAll();

			if (data.isEmpty()) {
				Instant    now        = Instant.now();
				long       nowDate    = now.toEpochMilli();
				long       nextScan   = nextPlannedDate(new Date(nowDate)).getTime();
				PluginData pluginData = new PluginData(nowDate, nowDate, nextScan);

				pluginDataList.add(pluginData);
				return;
			}

			for (Object[] result : data) {
				int  v              = 0;
				int  id             = (int) result[v++];
				long dateActivation = (long) result[v++];
				long scanDate       = (long) result[v++];
				long scheduledDate  = (long) result[v];

				PluginData pluginData = new PluginData(id, dateActivation, scanDate, scheduledDate);

				pluginDataList.add(pluginData);

				PluginData.setID(id);
			}
		});

	}

	public List<PluginData> getPluginDataList() {
		return Collections.unmodifiableList(pluginDataList);
	}

	public Date nextPlannedDate(Date currentDate) {
		LocalDateTime localDateTime = currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
		LocalDateTime updatedTime   = localDateTime.plusDays(SettingAddon.getCleanUpTime());

		return Date.from(updatedTime.atZone(ZoneId.systemDefault()).toInstant());
	}

}
