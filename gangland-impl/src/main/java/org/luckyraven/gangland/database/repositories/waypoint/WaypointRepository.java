package org.luckyraven.gangland.database.repositories.waypoint;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.database.tables.gang.GangTable;
import org.luckyraven.gangland.database.tables.waypoint.WaypointTable;
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

@Repository(Waypoint.class)
public class WaypointRepository extends AbstractRepository<Waypoint> {

	private final WaypointTable waypointTable;

	public WaypointRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.waypointTable = new WaypointTable(new GangTable());
	}

	@Override
	protected Collection<Waypoint> doLoadAll() throws SQLException {
		List<Waypoint> waypoints = new ArrayList<>();
		List<Object[]> data      = tableBackend().selectAll();

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
		tableBackend().delete("id = ?", data.getUsedId());
	}
}
