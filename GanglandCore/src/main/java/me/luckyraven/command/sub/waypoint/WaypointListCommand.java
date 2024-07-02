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

import java.util.List;
import java.util.Map;

class WaypointListCommand extends SubArgument {

	private final WaypointManager waypointManager;

	protected WaypointListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "list", tree, parent);

		this.waypointManager = gangland.getInitializer().getWaypointManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			StringBuilder builder = new StringBuilder();

			List<Waypoint> waypoints = waypointManager.getWaypoints()
													  .entrySet()
													  .stream()
													  .sorted(Map.Entry.comparingByKey())
													  .map(Map.Entry::getValue)
													  .toList();
			for (int i = 0; i < waypoints.size(); i++) {
				builder.append(waypoints.get(i).getName()).append(':').append(i + 1);

				if (i < waypoints.size() - 1) builder.append(", ");
			}

			sender.sendMessage(MessageAddon.WAYPOINT_LIST_PRIMARY.toString(),
							   MessageAddon.WAYPOINT_LIST_SECONDARY.toString()
																   .replace("%waypoints%", builder.toString()));
		};
	}

}
