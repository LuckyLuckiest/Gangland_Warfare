package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class WaypointInfoCommand extends SubArgument {

	private final WaypointManager waypointManager;

	protected WaypointInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("info", tree, parent);

		this.waypointManager = gangland.getInitializer().getWaypointManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player   player   = (Player) sender;
			Waypoint waypoint = waypointManager.getSelected(player);

			// check if the user already selected a waypoint
			if (waypoint == null) {
				player.sendMessage(MessageAddon.NOT_SELECTED_WAYPOINT.toString());
				return;
			}

			String type = String.format("&7Type:&b %s%s", waypoint.getType().getName(),
			                            waypoint.getType() == Waypoint.WaypointType.GANG ? ", Gang linked: " +
					                            waypoint.getGangId() : "");

			String[] info = {
					String.format("&8[&a%s&8]&7 info:", waypoint.getName()), "&7ID:&b " + waypoint.getUsedId(),
					"&7X:&b " + waypoint.getX(), "&7Y:&b " + waypoint.getY(), "&7Z:&b " + waypoint.getZ(),
					"&7Yaw:&b " + waypoint.getYaw(), "&7Pitch:&b " + waypoint.getPitch(),
					"&7World:&b " + waypoint.getWorld(), type, "&7Timer:&b " + waypoint.getTimer(),
					"&7Cooldown:&b " + waypoint.getCooldown(), "&7Shield:&b " + waypoint.getShield(),
					"&7Cost:&b " + waypoint.getCost(), "&7Radius:&b " + waypoint.getRadius()
			};

			player.sendMessage(ChatUtil.color(info));
		};
	}

}
