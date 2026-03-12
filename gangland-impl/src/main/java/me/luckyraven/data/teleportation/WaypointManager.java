package me.luckyraven.data.teleportation;

import me.luckyraven.Gangland;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.tables.waypoint.WaypointTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.repository.IRepository;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.sql.Types;
import java.util.*;

public class WaypointManager {

	private final Gangland               gangland;
	private final Map<Integer, Waypoint> waypoints;
	private final Map<Player, Waypoint>  selectedWaypoints;
	private final String                 prefix;

	public WaypointManager(Gangland gangland, String prefix) {
		this.gangland          = gangland;
		this.prefix            = prefix;
		this.waypoints         = new HashMap<>();
		this.selectedWaypoints = new HashMap<>();
	}

	public void initialize() {
		GanglandDatabase      database   = gangland.getInitializer().getGanglandDatabase();
		IRepository<Waypoint> repository = database.getRepositoryRegistry().getRepository(Waypoint.class);

		Collection<Waypoint> loaded = repository.loadAll();
		int                  maxId  = 0;

		for (Waypoint waypoint : loaded) {
			int id = waypoint.getUsedId();

			if (id > maxId) maxId = id;

			gangland.getInitializer().getPermissionManager().addPermission("waypoint." + id);
			waypoints.put(id, waypoint);
		}

		Waypoint.setID(maxId);

		repository.setDataSupplier(waypoints::values);
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
				.stream().filter(waypoint -> waypoint.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public void refactorIds(WaypointTable waypointTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			Database config = database.table(waypointTable.getName());

			List<Object[]> rowsData = config.selectAll();

			// remove all the data from the table

			config.delete("", null, Types.NULL);
			int tempId = 1;
			for (Object[] result : rowsData) {
				int id = (int) result[0];

				Waypoint waypoint = waypoints.get(id);
				waypoints.remove(waypoint.getUsedId());

				waypoint.setUsedId(tempId);
				waypoints.put(tempId, waypoint);

				waypointTable.insertTableQuery(database, waypoint);

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

	public int size() {
		return waypoints.size();
	}

	public Map<Player, Waypoint> getSelectedWaypoints() {
		return Collections.unmodifiableMap(selectedWaypoints);
	}

	@Override
	public String toString() {
		return String.format("waypoints=%s", waypoints);
	}

}
