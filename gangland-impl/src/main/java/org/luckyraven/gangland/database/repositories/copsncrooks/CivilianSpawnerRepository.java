package org.luckyraven.gangland.database.repositories.copsncrooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import org.luckyraven.gangland.database.tables.copsncrooks.CivilianSpawnerTable;
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

@Repository(CivilianSpawner.class)
public class CivilianSpawnerRepository extends AbstractRepository<CivilianSpawner> {

	private final CivilianSpawnerTable copSpawnerTable;

	public CivilianSpawnerRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.copSpawnerTable = new CivilianSpawnerTable();
	}

	@Override
	protected Collection<CivilianSpawner> doLoadAll() throws SQLException {
		List<CivilianSpawner> copSpawners = new ArrayList<>();
		List<Object[]>        data        = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			int    id     = (int) result[v++];
			String typeId = result[v] != null ? String.valueOf(result[v]) : null;
			v++;
			String groupId = result[v] != null ? String.valueOf(result[v]) : null;
			v++;
			String worldName = String.valueOf(result[v++]);
			double x         = (double) result[v++];
			double y         = (double) result[v++];
			double z         = (double) result[v++];
			double yaw       = (double) result[v++];
			double pitch     = (double) result[v];

			World world = Bukkit.getWorld(worldName);

			if (world == null) continue;
			Location location = new Location(world, x, y, z, (float) yaw, (float) pitch);

			copSpawners.add(new CivilianSpawner(id, location, typeId, groupId));
		}

		return copSpawners;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<CivilianSpawner> getTable() {
		return copSpawnerTable;
	}

	@Override
	protected void doDelete(CivilianSpawner data) throws SQLException {
		tableBackend().delete("id = ?", data.getId());
	}
}
