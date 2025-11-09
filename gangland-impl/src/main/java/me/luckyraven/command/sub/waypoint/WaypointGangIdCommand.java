package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class WaypointGangIdCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final WaypointManager     waypointManager;

	protected WaypointGangIdCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "gangId", tree, parent, "gang_id");

		this.gangland = gangland;
		this.tree     = tree;

		Initializer initializer = gangland.getInitializer();

		this.userManager     = initializer.getUserManager();
		this.gangManager     = initializer.getGangManager();
		this.waypointManager = initializer.getWaypointManager();

		waypointGangId();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<gangId>"));
		};
	}

	private void waypointGangId() {
		Argument optional = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player   = (Player) sender;
			User<Player> user     = userManager.getUser(player);
			Waypoint     waypoint = waypointManager.getSelected(player);

			if (waypoint == null) {
				player.sendMessage(MessageAddon.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			String value = args[2].toUpperCase();

			// verify if it was a number
			int id;
			try {
				id = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				sender.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// check if the gang is valid
			if (user.getGangId() != id) {
				player.sendMessage(MessageAddon.INVALID_GANG_NAME.toString());
				return;
			}

			Gang gang = gangManager.getGang(id);

			if (gang == null) {
				player.sendMessage(MessageAddon.GANG_DOESNT_EXIST.toString());
				return;
			}

			waypoint.setGangId(id);
			player.sendMessage(MessageAddon.WAYPOINT_CONFIGURATION_SUCCESS.toString());
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				return null;
			}

			int gangId = user.getGangId();

			List<String> waypointsByGang = waypointManager.getWaypoints().values()
					.stream().filter(waypoint -> waypoint.getGangId() == gangId).map(Waypoint::getName).toList();

			return new ArrayList<>(waypointsByGang);
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				return null;
			}

			int gangId = user.getGangId();

			Map<String, String> waypoints = new HashMap<>();

			List<Waypoint> waypointsByGang = waypointManager.getWaypoints().values()
					.stream().filter(waypoint -> waypoint.getGangId() == gangId).toList();

			for (Waypoint waypoint : waypointsByGang) {
				waypoints.put(waypoint.getName(), String.valueOf(waypoint.getUsedId()));
			}

			return waypoints;
		});

		this.addSubArgument(optional);
	}

}
