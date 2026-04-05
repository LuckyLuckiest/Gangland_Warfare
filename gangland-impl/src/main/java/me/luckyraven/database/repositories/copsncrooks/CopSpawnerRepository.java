package me.luckyraven.database.repositories.copsncrooks;

import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawner;
import me.luckyraven.database.tables.copsncrooks.CopSpawnerTable;
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

@Repository(CopSpawner.class)
public class CopSpawnerRepository extends AbstractRepository<CopSpawner> {

	private final CopSpawnerTable copSpawnerTable;

	public CopSpawnerRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.copSpawnerTable = new CopSpawnerTable();
	}

	@Override
	protected Collection<CopSpawner> doLoadAll() throws SQLException {
		List<CopSpawner> copSpawners = new ArrayList<>();
		List<Object[]>   data        = copSpawnerTable.selectAllTableQuery(getDatabase());

		for (Object[] result : data) {
			int v = 0;

			int    id        = (int) result[v++];
			String worldName = String.valueOf(result[v++]);
			double x         = (double) result[v++];
			double y         = (double) result[v++];
			double z         = (double) result[v++];
			double yaw       = (double) result[v++];
			double pitch     = (double) result[v];

			World world = Bukkit.getWorld(worldName);

			if (world == null) continue;
			Location location = new Location(world, x, y, z, (float) yaw, (float) pitch);

			copSpawners.add(new CopSpawner(id, location));

			CopSpawnManager.ID = id;
		}

		return copSpawners;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<CopSpawner> getTable() {
		return copSpawnerTable;
	}

	@Override
	protected void doDelete(CopSpawner data) throws SQLException {
		Database table = getDatabase().table(copSpawnerTable.getName());
		table.delete("id", data.getId(), Types.VARCHAR);
	}
}
