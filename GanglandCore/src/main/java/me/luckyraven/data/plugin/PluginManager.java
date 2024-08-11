package me.luckyraven.data.plugin;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.PluginDataTable;

import java.time.Instant;
import java.util.ArrayList;
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
				Date       nowDate    = new Date(now.getEpochSecond());
				PluginData pluginData = new PluginData(nowDate, (Date) nowDate.clone(), (Date) nowDate.clone());

				pluginDataList.add(pluginData);
			} else {
				for (Object[] result : data) {
					int v  = 0;
					int id = (int) result[v++];
//					Date dateActivation =
//					Date id = (int) result[v++];
				}
			}
		});
	}

}
