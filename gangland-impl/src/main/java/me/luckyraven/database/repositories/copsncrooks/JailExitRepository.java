package me.luckyraven.database.repositories.copsncrooks;

import me.luckyraven.copsncrooks.jail.JailExit;
import me.luckyraven.database.tables.copsncrooks.JailExitTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(JailExit.class)
public class JailExitRepository extends AbstractRepository<JailExit> {

	private final JailExitTable jailExitTable;

	public JailExitRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);
		this.jailExitTable = new JailExitTable();
	}

	@Override
	protected Collection<JailExit> doLoadAll() throws SQLException {
		List<JailExit> exits = new ArrayList<>();
		List<Object[]> data  = jailExitTable.selectAllTableQuery(getDatabase());

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
		Database table = getDatabase().table(jailExitTable.getName());
		int rowId = data.isGlobal()
		            ? JailExitTable.GLOBAL_ROW_ID
		            : (data.getJailId() == null ? JailExitTable.GLOBAL_ROW_ID : data.getJailId());
		table.delete("row_id", rowId, Types.INTEGER);
	}
}
