package org.luckyraven.gangland.database.repositories.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.data.plugin.PluginData;
import org.luckyraven.gangland.database.tables.plugin.PluginDataTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(PluginData.class)
public class PluginDataRepository extends AbstractRepository<PluginData> {

	/** A SQL NULL reads as 0; any other value is read through Number (MySQL may return Long/BigDecimal). */
	private static Number num(Object raw) {
		return raw instanceof Number number ? number : 0;
	}

	private final PluginDataTable pluginDataTable;

	public PluginDataRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.pluginDataTable = new PluginDataTable();
	}

	@Override
	protected Collection<PluginData> doLoadAll() throws SQLException {
		List<PluginData> pluginDataList = new ArrayList<>();
		List<Object[]>   data           = tableBackend().selectAll();

		for (Object[] result : data) {
			int  v              = 0;
			int  id             = num(result[v++]).intValue();
			long dateActivation = num(result[v++]).longValue();
			long scanDate       = num(result[v++]).longValue();
			long scheduledDate  = num(result[v]).longValue();

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
		tableBackend().delete("id = ?", data.getId());
	}
}
