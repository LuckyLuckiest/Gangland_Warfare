package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.CommandSender;

import java.util.Map;

class WaypointInfoCommand extends SubArgument {

	private final Gangland        gangland;
	private final Tree<Argument>  tree;
	private final WaypointManager waypointManager;

	protected WaypointInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland        = gangland;
		this.tree            = tree;
		this.waypointManager = gangland.getInitializer().getWaypointManager();

		this.nameArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void nameArgument() {
		Argument nameArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String   name     = args[2];
			Waypoint waypoint = waypointManager.get(name);

			if (waypoint == null) {
				sender.sendMessage(Messages.INVALID_WAYPOINT.toString());
				return;
			}

			String tpCommand = String.format("/%s teleport %s", Gangland.SHORT_PREFIX, waypoint.getName());

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
		}, sender -> waypointManager.getWaypoints()
		                            .entrySet()
				.stream()
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.map(Waypoint::getName)
				.toList());

		this.addSubArgument(nameArg);
	}

}
