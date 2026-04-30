package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

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

	protected WaypointGangIdCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                UserManager<Player> userManager, WaypointManager waypointManager,
	                                GangManager gangManager) {
		super(gangland, "gangId", tree, parent, "gang_id");

		this.gangland        = gangland;
		this.tree            = tree;
		this.userManager     = userManager;
		this.gangManager     = gangManager;
		this.waypointManager = waypointManager;

		waypointGangId();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gangId>"));
		};
	}

	private void waypointGangId() {
		Argument optional = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Waypoint waypoint = waypointManager.getSelected(player);

			if (waypoint == null) {
				user.sendMessage(Messages.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			OptionalArgument optionalArgument = (OptionalArgument) argument;

			String value = optionalArgument.getActualValue(args[2], sender);

			// verify if it was a number
			int id;
			try {
				id = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// check if the gang is valid
			if (user.getGangId() != id) {
				user.sendMessage(Messages.INVALID_GANG_NAME.toString());
				return;
			}

			Gang gang = gangManager.getGang(id);

			if (gang == null) {
				user.sendMessage(Messages.GANG_DOESNT_EXIST.toString());
				return;
			}

			waypoint.setGangId(id);
			user.sendMessage(Messages.WAYPOINT_CONFIGURATION_SUCCESS.toString());
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				return null;
			}

			int    gangId = user.getGangId();
			String name   = gangManager.getGang(gangId).getName();

			return new ArrayList<>(List.of(name));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				return null;
			}

			int gangId = user.getGangId();

			Map<String, String> waypoints = new HashMap<>();

			String name = gangManager.getGang(gangId).getName();

			waypoints.put(name, String.valueOf(gangId));

			return waypoints;
		});

		this.addSubArgument(optional);
	}

}
