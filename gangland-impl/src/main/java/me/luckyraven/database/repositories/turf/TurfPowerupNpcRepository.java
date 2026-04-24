package me.luckyraven.database.repositories.turf;

import me.luckyraven.copsncrooks.npc.turf.TurfPowerupData;
import me.luckyraven.database.tables.turf.TurfPowerupNpcTable;
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

@Repository(TurfPowerupData.class)
public class TurfPowerupNpcRepository extends AbstractRepository<TurfPowerupData> {

	private final TurfPowerupNpcTable table;

	public TurfPowerupNpcRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.table = new TurfPowerupNpcTable();
	}

	@Override
	protected Collection<TurfPowerupData> doLoadAll() throws SQLException {
		List<TurfPowerupData> rows = new ArrayList<>();
		for (Object[] result : table.selectAllTableQuery(getDatabase())) {
			int    v           = 0;
			int    turfId      = ((Number) result[v++]).intValue();
			String worldName   = String.valueOf(result[v++]);
			double x           = ((Number) result[v++]).doubleValue();
			double y           = ((Number) result[v++]).doubleValue();
			double z           = ((Number) result[v++]).doubleValue();
			double yaw         = ((Number) result[v++]).doubleValue();
			double pitch       = ((Number) result[v++]).doubleValue();
			Object displayName = result[v];

			World world = Bukkit.getWorld(worldName);
			if (world == null) continue;
			Location loc = new Location(world, x, y, z, (float) yaw, (float) pitch);
			rows.add(new TurfPowerupData(turfId, loc, displayName == null ? null : displayName.toString()));
		}
		return rows;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<TurfPowerupData> getTable() {
		return table;
	}

	@Override
	protected void doDelete(TurfPowerupData data) throws SQLException {
		Database db = getDatabase().table(table.getName());
		db.delete("turf_id", data.getTurfId(), Types.INTEGER);
	}
}
