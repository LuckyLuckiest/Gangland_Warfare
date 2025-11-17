package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

class WaypointSelectCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointSelectCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"select", "selects"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		Initializer initializer = gangland.getInitializer();

		this.userManager     = initializer.getUserManager();
		this.waypointManager = initializer.getWaypointManager();

		waypointSelect();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void waypointSelect() {
		// to select a waypoint, its id should be provided
		Argument select = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			OptionalArgument optionalArgument = (OptionalArgument) argument;

			// the id would be the second argument
			String argId = optionalArgument.getActualValue(args[2], sender);

			// verify if it was a number
			int id;
			try {
				id = Integer.parseInt(argId);
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", argId));
				return;
			}

			Waypoint waypoint = waypointManager.get(id);

			// check if the waypoint exists
			if (waypoint == null) {
				sender.sendMessage(MessageAddon.INVALID_WAYPOINT.toString());
				return;
			}

			// add the waypoint to waypoint manager
			Player player = (Player) sender;
			waypointManager.playerSelect(player, waypoint);

			// inform the player
			player.sendMessage(MessageAddon.WAYPOINT_SELECTED.toString().replace("%waypoint%", waypoint.getName()));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

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
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

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
		});

		this.addSubArgument(select);
	}

}
