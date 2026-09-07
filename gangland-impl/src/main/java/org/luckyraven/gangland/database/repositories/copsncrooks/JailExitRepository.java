package org.luckyraven.gangland.database.repositories.copsncrooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.jail.JailExit;
import org.luckyraven.gangland.database.tables.copsncrooks.JailExitTable;
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

@Repository(JailExit.class)
public class JailExitRepository extends AbstractRepository<JailExit> {

	private final JailExitTable jailExitTable;

	public JailExitRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.jailExitTable = new JailExitTable();
	}

	@Override
	protected Collection<JailExit> doLoadAll() throws SQLException {
		List<JailExit> exits = new ArrayList<>();
		List<Object[]> data  = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 1; // skip row_id column

			String  scopeName = String.valueOf(result[v++]);
			Object  rawJailId = result[v++];
			Integer jailId    = rawJailId == null ? null : ((Number) rawJailId).intValue();
			String  worldName = String.valueOf(result[v++]);
			double  x         = ((Number) result[v++]).doubleValue();
			double  y         = ((Number) result[v++]).doubleValue();
			double  z         = ((Number) result[v++]).doubleValue();
			float   yaw       = ((Number) result[v++]).floatValue();
			float   pitch     = ((Number) result[v]).floatValue();

			World world = Bukkit.getWorld(worldName);
			if (world == null) continue;

			Location location = new Location(world, x, y, z, yaw, pitch);

			JailExit.Scope scope;
			try {
				scope = JailExit.Scope.valueOf(scopeName);
			} catch (IllegalArgumentException e) {
				scope = jailId == null ? JailExit.Scope.GLOBAL : JailExit.Scope.SPECIFIC;
			}

			if (scope == JailExit.Scope.GLOBAL) {
				exits.add(JailExit.global(location));
			} else if (jailId != null) {
				exits.add(JailExit.forJail(jailId, location));
			}
		}

		return exits;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<JailExit> getTable() {
		return jailExitTable;
	}

	@Override
	protected void doDelete(JailExit data) throws SQLException {
		int rowId = data.isGlobal()
		            ? JailExitTable.GLOBAL_ROW_ID
		            : (data.getJailId() == null ? JailExitTable.GLOBAL_ROW_ID : data.getJailId());
		tableBackend().delete("row_id = ?", rowId);
	}
}
