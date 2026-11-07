package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab-completion sources shared by the waypoint select, delete and teleport commands.
 */
final class WaypointCompletion {

	private WaypointCompletion() {
	}

	/**
	 * Names of the waypoints the sender may use: the gang's waypoints first, then the permitted ones. Returns
	 * {@code null} when the sender has no user.
	 */
	static List<String> names(UserManager<Player> userManager, WaypointManager waypointManager, CommandSender sender) {
		Player       player = (Player) sender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return null;

		List<String> waypoints = new ArrayList<>();

		Collection<Waypoint> allWaypoints = waypointManager.getWaypoints().values();
		if (user.hasGang()) {
			int gangId = user.getGangId();

			List<String> list = allWaypoints.stream()
					.filter(waypoint -> waypoint.getGangId() == gangId)
					.map(Waypoint::getName)
					.toList();

			waypoints.addAll(list);
		}

		List<String> list = allWaypoints.stream()
				.filter(waypoint -> player.hasPermission(waypoint.getPermission()))
				.map(Waypoint::getName)
				.toList();

		waypoints.addAll(list);

		return waypoints;
	}

	/**
	 * Display name to waypoint id for the waypoints the sender may use; a name shared by several waypoints is shown as
	 * {@code name:usedId}. Returns {@code null} when the sender has no user.
	 */
	static Map<String, String> nameToIdMap(UserManager<Player> userManager, WaypointManager waypointManager,
	                                       CommandSender sender) {
		Player       player = (Player) sender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return null;

		List<Waypoint> waypoints = new ArrayList<>();

		Collection<Waypoint> allWaypoints = waypointManager.getWaypoints().values();
		if (user.hasGang()) {
			int gangId = user.getGangId();

			List<Waypoint> list = allWaypoints.stream().filter(waypoint -> waypoint.getGangId() == gangId).toList();

			waypoints.addAll(list);
		}

		List<Waypoint> list = allWaypoints.stream()
				.filter(waypoint -> player.hasPermission(waypoint.getPermission()))
				.toList();

		waypoints.addAll(list);

		// First pass: count how many times each name appears
		Map<String, Integer> nameCount = new HashMap<>();
		for (Waypoint waypoint : waypoints) {
			String name = waypoint.getName();
			nameCount.put(name, nameCount.getOrDefault(name, 0) + 1);
		}

		// Second pass: build the map with name:id for duplicates
		Map<String, String> waypointMap = new HashMap<>();
		for (Waypoint waypoint : waypoints) {
			String name        = waypoint.getName();
			String displayName = nameCount.get(name) > 1 ? name + ":" + waypoint.getUsedId() : name;

			waypointMap.put(displayName, String.valueOf(waypoint.getUsedId()));
		}

		return waypointMap;
	}

}
