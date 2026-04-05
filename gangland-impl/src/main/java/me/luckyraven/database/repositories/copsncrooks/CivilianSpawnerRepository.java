package me.luckyraven.database.repositories.copsncrooks;

import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.database.tables.copsncrooks.CivilianSpawnerTable;
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

@Repository(CivilianSpawner.class)
public class CivilianSpawnerRepository extends AbstractRepository<CivilianSpawner> {

	private final CivilianSpawnerTable copSpawnerTable;

	public CivilianSpawnerRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.copSpawnerTable = new CivilianSpawnerTable();
	}

	@Override
	protected Collection<CivilianSpawner> doLoadAll() throws SQLException {
		List<CivilianSpawner> copSpawners = new ArrayList<>();
		List<Object[]>        data        = copSpawnerTable.selectAllTableQuery(getDatabase());

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

			CopSpawnManager.ID = id;
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
		Database table = getDatabase().table(copSpawnerTable.getName());
		table.delete("id", data.getId(), Types.VARCHAR);
	}
}
