package org.luckyraven.gangland.command.sub.waypoint;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointAccess;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Qualifier;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;
import java.util.Map;

class WaypointInfoCommand extends SubArgument {

	private final JavaPlugin        gangland;
	private final Tree<Argument>  tree;
	private final WaypointManager waypointManager;
	private final UserManager<Player> userManager;

	protected WaypointInfoCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                              @Qualifier("online") UserManager<Player> userManager,
	                              WaypointManager waypointManager) {
		super(gangland, "info", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.waypointManager = waypointManager;
		this.userManager     = userManager;

		this.nameArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void nameArgument() {
		Argument nameArg = new OptionalArgument(gangland, tree,
		                                        (argument, sender, args) -> show(sender, userManager, waypointManager,
		                                                                         args[2]),
		                                        sender -> suggest(sender, userManager, waypointManager));

		this.addSubArgument(nameArg);
	}

	static void show(CommandSender sender, UserManager<Player> userManager, WaypointManager waypointManager,
	                 String name) {
		Waypoint waypoint = waypointManager.get(name);

		// LS-01: a player sees only what WaypointAccess lets them use; a hidden waypoint answers as unknown so its
		// existence is not leaked. The console keeps the full view.
		if (waypoint == null || (sender instanceof Player player && !WaypointAccess.canAccess(
				userManager.getUser(player), waypoint))) {
			sender.sendMessage(Messages.INVALID_WAYPOINT.toString());
			return;
		}

		String tpCommand = String.format("/%s teleport %s", GanglandApi.SHORT_PREFIX, waypoint.getName());

		String color = GanglandChatUtil.color("&7&lWaypoint &e(&b" + waypoint.getName() + "&e)&7: ");
		var message = new ComponentBuilder(color).append(GanglandChatUtil.color("&e(&btp&e)"))
		                                         .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
		                                         .create();

		sender.spigot().sendMessage(message);

		String type = String.format("&bType: &7%s%s", waypoint.getType().getName(),
		                            waypoint.getType() == Waypoint.WaypointType.GANG ?
		                            ", Gang: " + waypoint.getGangId() : "");

		String info = String.format(
				" &bX: &7%d\n &bY: &7%d\n &bZ: &7%d\n &bWorld: &7%s\n %s\n &bTimer: &7%d\n &bCooldown: &7%d\n &bShield: &7%d\n &bCost: &7%.1f\n &bRadius: &7%.1f",
				(int) waypoint.getX(), (int) waypoint.getY(), (int) waypoint.getZ(), waypoint.getWorld(), type,
				waypoint.getTimer(), waypoint.getCooldown(), waypoint.getShield(), waypoint.getCost(),
				waypoint.getRadius());

		sender.sendMessage(GanglandChatUtil.color(info));
	}

	static List<String> suggest(CommandSender sender, UserManager<Player> userManager,
	                            WaypointManager waypointManager) {
		return waypointManager.getWaypoints()
				.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.filter(waypoint -> !(sender instanceof Player player) ||
				                    WaypointAccess.canAccess(userManager.getUser(player), waypoint))
				.map(Waypoint::getName)
				.toList();
	}

}
