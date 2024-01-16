package me.luckyraven.data.teleportation;

import me.luckyraven.Gangland;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.WaypointDatabase;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaypointManager {

	private final Gangland               gangland;
	private final Map<Integer, Waypoint> waypoints;
	private final Map<Player, Waypoint>  selectedWaypoints;

	public WaypointManager(Gangland gangland) {
		this.gangland = gangland;
		this.waypoints = new HashMap<>();
		this.selectedWaypoints = new HashMap<>();
	}

	public void initialize(WaypointDatabase waypointDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, waypointDatabase);

		helper.runQueries(database -> {
			Database config = database.table("data");

			List<Object[]> data = config.selectAll();

			for (Object[] result : data) {
				int    v        = 0;
				int    id       = (int) result[v++];
				String name     = String.valueOf(result[v++]);
				String world    = String.valueOf(result[v++]);
				double x        = (double) result[v++];
				double y        = (double) result[v++];
				double z        = (double) result[v++];
				double yaw      = (double) result[v++];
				double pitch    = (double) result[v++];
				String type     = String.valueOf(result[v++]);
				int    gangId   = (int) result[v++];
				int    timer    = (int) result[v++];
				int    cooldown = (int) result[v++];
				int    shield   = (int) result[v++];
				double cost     = (double) result[v++];
				double radius   = (double) result[v];

				Waypoint waypoint = new Waypoint(name);

				waypoint.setCoordinates(world, x, y, z, (float) yaw, (float) pitch);
				waypoint.setType(Waypoint.WaypointType.valueOf(type.toUpperCase()));
				waypoint.setGangId(gangId);
				waypoint.setTimer(timer);
				waypoint.setCooldown(cooldown);
				waypoint.setShield(shield);
				waypoint.setCost(cost);
				waypoint.setRadius(radius);

				gangland.getInitializer().getPermissionManager().addPermission("waypoint." + id);

				waypoints.put(id, waypoint);
			}
		});
	}

	public void add(Waypoint waypoint) {
		waypoints.put(waypoint.getUsedId(), waypoint);
	}

	public boolean remove(Waypoint waypoint) {
		Waypoint w = waypoints.remove(waypoint.getUsedId());
		return w != null;
	}

	public void clear() {
		Waypoint.setID(0);
		waypoints.clear();
		selectedWaypoints.clear();
	}

	@Nullable
	public Waypoint get(int id) {
		return waypoints.get(id);
	}

	@Nullable
	public Waypoint get(String name) {
		return waypoints.values()
						.stream()
						.filter(waypoint -> waypoint.getName().equalsIgnoreCase(name))
						.findFirst()
						.orElse(null);
	}

	public void refactorIds(WaypointDatabase waypointDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, waypointDatabase);

		helper.runQueries(database -> {
			Database config = database.table("data");

			List<Object[]> rowsData = config.selectAll();

			// remove all the data from the table
			config.delete("", "");

			int tempId = 1;
			for (Object[] result : rowsData) {
				int id = (int) result[0];

				Waypoint waypoint = waypoints.get(id);
				waypoints.remove(waypoint.getUsedId());

				waypoint.setUsedId(tempId);
				waypoints.put(tempId, waypoint);

				waypointDatabase.insertDataTable(waypoint);

				tempId++;
			}

			Waypoint.setID(tempId - 1);
		});
	}

	public void playerSelect(Player player, Waypoint waypoint) {
		selectedWaypoints.put(player, waypoint);
	}

	public Waypoint getSelected(Player player) {
		return selectedWaypoints.get(player);
	}

	public Waypoint playerDeselect(Player player) {
		return selectedWaypoints.remove(player);
	}

	public Map<Integer, Waypoint> getWaypoints() {
		return Collections.unmodifiableMap(waypoints);
	}

	public Map<Player, Waypoint> getSelectedWaypoints() {
		return Collections.unmodifiableMap(selectedWaypoints);
	}

	@Override
	public String toString() {
		return String.format("waypoints=%s", waypoints);
	}

}
