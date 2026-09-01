package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Arrays;

class WaypointTypeCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointTypeCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager, WaypointManager waypointManager) {
		super(gangland, "type", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.userManager     = userManager;
		this.waypointManager = waypointManager;

		waypointType();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<value>"));
		};
	}

	private void waypointType() {
		Argument optional = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Waypoint waypoint = waypointManager.getSelected(player);

			if (waypoint == null) {
				user.sendMessage(Messages.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			String value = args[2].toUpperCase();

			// check if the type exists
			Waypoint.WaypointType type;
			try {
				type = Waypoint.WaypointType.valueOf(value);
				user.sendMessage(Messages.WAYPOINT_CONFIGURATION_SUCCESS.toString());
			} catch (IllegalArgumentException exception) {
				StringBuilder           builder       = new StringBuilder();
				Waypoint.WaypointType[] waypointTypes = Waypoint.WaypointType.values();

				for (int i = 0; i < waypointTypes.length; i++) {
					builder.append(waypointTypes[i].getName());

					if (i < waypointTypes.length - 1) builder.append(", ");
				}

				user.sendMessage(Messages.WAYPOINT_TYPE_INVALID_HEADER.toString(),
				                 GanglandChatUtil.color("&7" + builder));

				type = waypoint.getType();
			}

			waypoint.setType(type);
		}, sender -> {
			Waypoint.WaypointType[] types = Waypoint.WaypointType.values();

			return Arrays.stream(types).map(Waypoint.WaypointType::getName).toList();
		});

		this.addSubArgument(optional);
	}

}
