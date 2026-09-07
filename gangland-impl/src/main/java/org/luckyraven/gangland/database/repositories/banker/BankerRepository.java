package org.luckyraven.gangland.database.repositories.banker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerData;
import org.luckyraven.gangland.database.tables.banker.BankerTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Repository(BankerData.class)
public class BankerRepository extends AbstractRepository<BankerData> {

	private final BankerTable table;

	public BankerRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.table = new BankerTable();
	}

	@Override
	protected Collection<BankerData> doLoadAll() throws SQLException {
		List<BankerData> list = new ArrayList<>();
		List<Object[]>   data = tableBackend().selectAll();

		for (Object[] row : data) {
			int v = 0;

			UUID   id          = UUID.fromString(String.valueOf(row[v++]));
			String worldName   = String.valueOf(row[v++]);
			double x           = ((Number) row[v++]).doubleValue();
			double y           = ((Number) row[v++]).doubleValue();
			double z           = ((Number) row[v++]).doubleValue();
			double yaw         = ((Number) row[v++]).doubleValue();
			double pitch       = ((Number) row[v++]).doubleValue();
			Object rawName     = row[v];
			String displayName = rawName == null ? null : String.valueOf(rawName);

			World    world    = Bukkit.getWorld(worldName);
			Location location = new Location(world, x, y, z, (float) yaw, (float) pitch);

			list.add(new BankerData(id, location, displayName));
		}

		return list;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<BankerData> getTable() {
		return table;
	}

	@Override
	protected void doDelete(BankerData data) throws SQLException {
		tableBackend().delete("id = ?", data.getId().toString());
	}

}
