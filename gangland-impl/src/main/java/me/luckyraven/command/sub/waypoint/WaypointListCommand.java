package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;

import java.util.Map;

class WaypointListCommand extends SubArgument {

	private final WaypointManager waypointManager;

	protected WaypointListCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              WaypointManager waypointManager) {
		super(gangland, "list", tree, parent);

		this.waypointManager = waypointManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(Messages.WAYPOINT_LIST_HEADER.toString());

			waypointManager.getWaypoints()
			               .entrySet()
					.stream()
					.sorted(Map.Entry.comparingByKey())
					.map(Map.Entry::getValue)
					.forEach(waypoint -> {
						String name      = waypoint.getName();
						String tpCommand = String.format("/%s teleport %s", Gangland.SHORT_PREFIX, name);
						String hoverText = String.format("%s - %d, %d, %d", waypoint.getWorld(),
						                                 (int) waypoint.getX(), (int) waypoint.getY(),
						                                 (int) waypoint.getZ());

						var message = new ComponentBuilder(GanglandChatUtil.color(" &b- &7" + name + " "))
								.append(GanglandChatUtil.color("&e(&btp&e)"))
								.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
								.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)))
								.create();

						sender.spigot().sendMessage(message);
					});
		};
	}

}
