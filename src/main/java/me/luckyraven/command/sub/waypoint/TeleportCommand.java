package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.teleportation.IllegalTeleportException;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.teleportation.WaypointTeleport;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.TimeUtil;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class TeleportCommand extends CommandHandler {

	public TeleportCommand(Gangland gangland) {
		super(gangland, "teleport", true, "tp");

		List<CommandInformation> list = getCommands().entrySet().parallelStream().filter(
				entry -> entry.getKey().startsWith("waypoint")).sorted(Map.Entry.comparingByKey()).map(
				Map.Entry::getValue).toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		UserManager<Player> userManager     = getGangland().getInitializer().getUserManager();
		WaypointManager     waypointManager = getGangland().getInitializer().getWaypointManager();

		Player   player   = (Player) commandSender;
		Waypoint waypoint = waypointManager.getSelected(player);

		teleport(commandSender, waypoint, userManager);
	}

	@Override
	protected void initializeArguments() {
		UserManager<Player> userManager     = getGangland().getInitializer().getUserManager();
		WaypointManager     waypointManager = getGangland().getInitializer().getWaypointManager();

		Argument name = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Waypoint waypoint = waypointManager.get(args[1]);

			teleport(sender, waypoint, userManager);
		});

		getArgument().addPermission(getPermission() + ".cooldown_bypass");

		getArgument().addSubArgument(name);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Waypoint");
	}

	private void teleport(CommandSender sender, Waypoint waypoint, UserManager<Player> userManager) {
		if (waypoint == null) {
			sender.sendMessage(MessageAddon.INVALID_WAYPOINT.toString());
			return;
		}

		Player       player = (Player) sender;
		User<Player> user   = userManager.getUser(player);

		try {
			if (player.hasPermission("gangland.command.teleport.cooldown_bypass")) WaypointTeleport.removeCooldown(
					user);

			waypoint.getWaypointTeleport().teleport(getGangland(), user, (u, t) -> {
				String time    = TimeUtil.formatTime(t.getTimeLeft(), true);
				String message = MessageAddon.WAYPOINT_TELEPORT_TIMER.toString().replace("%timer%", time);

				u.getUser().sendMessage(message);
			}).thenAccept(teleportResult -> {
				String message;
				if (teleportResult.success()) {
					message = MessageAddon.WAYPOINT_TELEPORT.toString().replace("%location%",
					                                                            teleportResult.waypoint().getName());

				} else {
					message = MessageAddon.UNABLE_TELEPORT_WAYPOINT.toString().replace("%location%",
					                                                                   teleportResult.waypoint()
					                                                                                 .getName());

				}
				teleportResult.playerUser().getUser().sendMessage(message);
			});
		} catch (IllegalTeleportException exception) {
			CountdownTimer timer = WaypointTeleport.getCooldownTimer(user);

			if (timer == null) return;

			String time    = TimeUtil.formatTime(timer.getTimeLeft(), true);
			String message = MessageAddon.WAYPOINT_TELEPORT_COOLDOWN.toString().replace("%timer%", time);

			player.sendMessage(message);
		}
	}

}
