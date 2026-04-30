package org.luckyraven.gangland.database.repositories.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.data.plugin.PluginData;
import org.luckyraven.gangland.database.tables.plugin.PluginDataTable;
import org.luckyraven.gangland.persistence.database.Database;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.repository.AbstractRepository;
import org.luckyraven.gangland.persistence.repository.Repository;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(PluginData.class)
public class PluginDataRepository extends AbstractRepository<PluginData> {

	private final PluginDataTable pluginDataTable;

	public PluginDataRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.pluginDataTable = new PluginDataTable();
	}

	@Override
	protected Collection<PluginData> doLoadAll() throws SQLException {
		List<PluginData> pluginDataList = new ArrayList<>();
		List<Object[]>   data           = pluginDataTable.selectAllTableQuery(getDatabase());

		for (Object[] result : data) {
			int  v              = 0;
			int  id             = (int) result[v++];
			long dateActivation = (long) result[v++];
			long scanDate       = (long) result[v++];
			long scheduledDate  = (long) result[v];

			pluginDataList.add(new PluginData(id, dateActivation, scanDate, scheduledDate));
		}

		return pluginDataList;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<PluginData> getTable() {
		return pluginDataTable;
	}

	@Override
	protected void doDelete(PluginData data) throws SQLException {
		Database table = getDatabase().table(pluginDataTable.getName());
		table.delete("id", data.getId(), Types.INTEGER);
	}
}
