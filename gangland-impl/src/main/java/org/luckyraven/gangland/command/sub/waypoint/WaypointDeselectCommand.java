package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

class WaypointDeselectCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointDeselectCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                  UserManager<Player> userManager, WaypointManager waypointManager) {
		super(gangland, "deselect", tree, parent);

		this.userManager     = userManager;
		this.waypointManager = waypointManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			// player has a selected waypoint
			Waypoint waypoint = waypointManager.playerDeselect(player);

			if (waypoint != null) {
				user.sendMessage(Messages.WAYPOINT_DESELECTED.toString().replace("%waypoint%", waypoint.getName()));
			}
			// player didn't select a waypoint
			else {
				user.sendMessage(Messages.NOT_SELECTED_WAYPOINT.toString());
			}
		};
	}

}
