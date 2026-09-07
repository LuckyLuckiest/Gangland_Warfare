package org.luckyraven.gangland.command.sub.waypoint;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointAccess;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

class WaypointListCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	protected WaypointListCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager, WaypointManager waypointManager) {
		super(gangland, "list", tree, parent);

		this.userManager     = userManager;
		this.waypointManager = waypointManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			// LS-01: the list used to print every waypoint on the server — name, world and coordinates — with a
			// click-to-run teleport component, which enumerated every gang base for anyone holding the command node.
			Collection<Waypoint> all = waypointManager.getWaypoints().values();

			// The console is already trusted with every admin waypoint command, so it keeps the full list.
			List<Waypoint> reachable;
			if (sender instanceof Player player) {
				reachable = WaypointAccess.accessible(userManager.getUser(player), all);
			} else {
				reachable = List.copyOf(all);
			}

			List<Waypoint> visible = reachable.stream()
					.sorted(Comparator.comparingInt(Waypoint::getUsedId))
					.toList();

			if (visible.isEmpty()) {
				sender.sendMessage(Messages.WAYPOINT_LIST_EMPTY.toString());
				return;
			}

			sender.sendMessage(Messages.WAYPOINT_LIST_HEADER.toString());

			for (Waypoint waypoint : visible) {
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
			}
		};
	}

}
