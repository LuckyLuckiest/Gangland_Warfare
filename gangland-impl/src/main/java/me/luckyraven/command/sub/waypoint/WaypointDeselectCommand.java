package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class WaypointDeselectCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointDeselectCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "deselect", tree, parent);

		this.userManager     = gangland.getInitializer().getUserManager();
		this.waypointManager = gangland.getInitializer().getWaypointManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			// player has a selected waypoint
			Waypoint waypoint = waypointManager.playerDeselect(player);

			if (waypoint != null) {
				user.sendMessage(
						MessageAddon.WAYPOINT_DESELECTED.toString().replace("%waypoint%", waypoint.getName()));
			}
			// player didn't select a waypoint
			else {
				user.sendMessage(MessageAddon.NOT_SELECTED_WAYPOINT.toString());
			}
		};
	}

}
