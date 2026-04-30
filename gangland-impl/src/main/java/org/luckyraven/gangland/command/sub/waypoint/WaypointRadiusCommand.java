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
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

class WaypointRadiusCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointRadiusCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                UserManager<Player> userManager, WaypointManager waypointManager) {
		super(gangland, "radius", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.userManager     = userManager;
		this.waypointManager = waypointManager;

		waypointRadius();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private void waypointRadius() {
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

			// verify if it was a number
			double changedValue;
			try {
				changedValue = Double.parseDouble(value);
			} catch (NumberFormatException exception) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// update the timer
			waypoint.setRadius(changedValue);
			user.sendMessage(Messages.WAYPOINT_CONFIGURATION_SUCCESS.toString());
		}, sender -> List.of("<radius>"));

		this.addSubArgument(optional);
	}

}
