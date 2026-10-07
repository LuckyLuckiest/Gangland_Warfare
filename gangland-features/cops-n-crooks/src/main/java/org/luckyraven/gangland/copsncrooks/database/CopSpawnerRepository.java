package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawner;
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

@Repository(CopSpawner.class)
public class CopSpawnerRepository extends AbstractRepository<CopSpawner> {

	private final CopSpawnerTable copSpawnerTable;
	private       int             highestStoredId;

	public CopSpawnerRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.copSpawnerTable = new CopSpawnerTable();
	}

	/** The highest id of every row the last load saw, rows of unloaded worlds included (docket T-180). */
	public int getHighestStoredId() {
		return highestStoredId;
	}

	@Override
	protected Collection<CopSpawner> doLoadAll() throws SQLException {
		List<CopSpawner> copSpawners = new ArrayList<>();
		List<Object[]>   data        = tableBackend().selectAll();

		highestStoredId = 0;

		for (Object[] result : data) {
			int v = 0;

			int    id        = (int) result[v++];
			// before the unloaded-world skip: the next created spawner must never reuse that row's id
			highestStoredId = Math.max(highestStoredId, id);
			String worldName = String.valueOf(result[v++]);
			double x         = (double) result[v++];
			double y         = (double) result[v++];
			double z         = (double) result[v++];
			double yaw       = (double) result[v++];
			double pitch     = (double) result[v++];
			// station_id was appended in 0.16: an older table has no such index
			Integer stationId = result.length > v && result[v] != null ? ((Number) result[v]).intValue() : null;

			World world = Bukkit.getWorld(worldName);

			if (world == null) continue;
			Location location = new Location(world, x, y, z, (float) yaw, (float) pitch);

			CopSpawner spawner = new CopSpawner(id, location);
			spawner.setStationId(stationId);
			copSpawners.add(spawner);
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
		tableBackend().delete("id = ?", data.getId());
	}
}
