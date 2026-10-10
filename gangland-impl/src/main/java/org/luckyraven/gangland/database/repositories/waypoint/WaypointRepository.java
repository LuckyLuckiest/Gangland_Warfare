package org.luckyraven.gangland.database.repositories.waypoint;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.teleportation.Waypoint;
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

@CustomLog
@Repository(Waypoint.class)
public class WaypointRepository extends AbstractRepository<Waypoint> {

	/** A SQL NULL reads as 0; any other value is read through Number (MySQL may return Long/BigDecimal). */
	private static Number num(Object raw) {
		return raw instanceof Number number ? number : 0;
	}

	private final WaypointTable waypointTable;
	private       int           highestStoredId;

	public WaypointRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.waypointTable = new WaypointTable();
	}

	/** The highest id of every row the last load saw, rows skipped for an unknown type included. */
	public int getHighestStoredId() {
		return highestStoredId;
	}

	@Override
	protected Collection<Waypoint> doLoadAll() throws SQLException {
		List<Waypoint> waypoints = new ArrayList<>();
		List<Object[]> data      = tableBackend().selectAll();

		highestStoredId = 0;

		for (Object[] result : data) {
			int    v        = 0;
			int    id       = num(result[v++]).intValue();
			// before the unknown-type skip: the next created waypoint must never reuse that row's id
			highestStoredId = Math.max(highestStoredId, id);
			int    gangId   = num(result[v++]).intValue();
			String name     = String.valueOf(result[v++]);
			String world    = String.valueOf(result[v++]);
			double x        = num(result[v++]).doubleValue();
			double y        = num(result[v++]).doubleValue();
			double z        = num(result[v++]).doubleValue();
			double yaw      = num(result[v++]).doubleValue();
			double pitch    = num(result[v++]).doubleValue();
			String type     = String.valueOf(result[v++]);
			int    shield   = num(result[v++]).intValue();
			int    timer    = num(result[v++]).intValue();
			int    cooldown = num(result[v++]).intValue();
			double cost     = num(result[v++]).doubleValue();
			double radius   = num(result[v]).doubleValue();

			// R35: a type this version does not know (a row written by a newer one) skips the row, not the whole load
			Waypoint.WaypointType waypointType;
			try {
				waypointType = Waypoint.WaypointType.valueOf(type.toUpperCase());
			} catch (IllegalArgumentException e) {
				log.warn("Skipping waypoint '{}' (id {}): unknown type '{}'.", name, id, type);
				continue;
			}

			Waypoint waypoint = new Waypoint(name, Gangland.FULL_PREFIX);
			waypoint.setUsedId(id);
			waypoint.setCoordinates(world, x, y, z, (float) yaw, (float) pitch);
			waypoint.setType(waypointType);
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
