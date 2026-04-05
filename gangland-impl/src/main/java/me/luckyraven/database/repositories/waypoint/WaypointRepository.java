package me.luckyraven.database.repositories.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.database.tables.gang.GangTable;
import me.luckyraven.database.tables.waypoint.WaypointTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Waypoint.class)
public class WaypointRepository extends AbstractRepository<Waypoint> {

	private final WaypointTable waypointTable;

	public WaypointRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.waypointTable = new WaypointTable(new GangTable());
	}

	@Override
	protected Collection<Waypoint> doLoadAll() throws SQLException {
		List<Waypoint> waypoints = new ArrayList<>();
		List<Object[]> data      = waypointTable.selectAllTableQuery(getDatabase());

		for (Object[] result : data) {
			int    v        = 0;
			int    id       = (int) result[v++];
			int    gangId   = (int) result[v++];
			String name     = String.valueOf(result[v++]);
			String world    = String.valueOf(result[v++]);
			double x        = (double) result[v++];
			double y        = (double) result[v++];
			double z        = (double) result[v++];
			double yaw      = (double) result[v++];
			double pitch    = (double) result[v++];
			String type     = String.valueOf(result[v++]);
			int    shield   = (int) result[v++];
			int    timer    = (int) result[v++];
			int    cooldown = (int) result[v++];
			double cost     = (double) result[v++];
			double radius   = (double) result[v];

			Waypoint waypoint = new Waypoint(name, Gangland.FULL_PREFIX);
			waypoint.setUsedId(id);
			waypoint.setCoordinates(world, x, y, z, (float) yaw, (float) pitch);
			waypoint.setType(Waypoint.WaypointType.valueOf(type.toUpperCase()));
			waypoint.setGangId(gangId);
			waypoint.setTimer(timer);
			waypoint.setCooldown(cooldown);
			waypoint.setShield(shield);
			waypoint.setCost(cost);
			waypoint.setRadius(radius);

			waypoints.add(waypoint);
		}

		return waypoints;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Waypoint> getTable() {
		return waypointTable;
	}

	@Override
	protected void doDelete(Waypoint data) throws SQLException {
		Database table = getDatabase().table(waypointTable.getName());
		table.delete("id", data.getUsedId(), Types.INTEGER);
	}
}
