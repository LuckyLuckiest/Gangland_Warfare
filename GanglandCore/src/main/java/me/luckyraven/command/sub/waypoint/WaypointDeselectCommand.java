package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class WaypointDeselectCommand extends SubArgument {

	private final WaypointManager waypointManager;

	protected WaypointDeselectCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "deselect", tree, parent);

		this.waypointManager = gangland.getInitializer().getWaypointManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player player = (Player) sender;

			// player has a selected waypoint
			Waypoint waypoint = waypointManager.playerDeselect(player);

			if (waypoint != null) {
				player.sendMessage(
						MessageAddon.WAYPOINT_DESELECTED.toString().replace("%waypoint%", waypoint.getName()));
			}
			// player didn't select a waypoint
			else {
				player.sendMessage(MessageAddon.NOT_SELECTED_WAYPOINT.toString());
			}
		};
	}

}
