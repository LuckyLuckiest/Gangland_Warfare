package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
