package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
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

		Player       player   = (Player) commandSender;
		User<Player> user     = userManager.getUser(player);
		Waypoint     waypoint = waypointManager.getSelected(player);

		if (waypoint == null) {
			help(commandSender, 1);
			return;
		}

		waypoint.teleport(getGangland(), user, (u, t) -> {
			u.getUser().sendMessage(ChatUtil.color("Teleporting in &b" + t.getTimeLeft() + "&7 seconds."));
		}).thenAccept(teleportResult -> {
			if (teleportResult.success()) {
				teleportResult.playerUser().getUser().sendMessage(
						ChatUtil.prefixMessage("Successfully teleported to &b" + teleportResult.waypoint().getName()));
			} else {
				teleportResult.playerUser().getUser().sendMessage(
						ChatUtil.prefixMessage("Unable to teleport to &b" + teleportResult.waypoint().getName()));
			}
		});
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		UserManager<Player> userManager     = gangland.getInitializer().getUserManager();
		WaypointManager     waypointManager = gangland.getInitializer().getWaypointManager();

		Argument name = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Waypoint waypoint = waypointManager.get(args[1]);

			if (waypoint == null) {
				sender.sendMessage(MessageAddon.INVALID_WAYPOINT.toString());
				return;
			}

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			waypoint.teleport(getGangland(), user, (u, t) -> {
				u.getUser().sendMessage(ChatUtil.color("&7Teleporting in &b" + t.getTimeLeft() + "&7 seconds."));
			}).thenAccept(teleportResult -> {
				if (teleportResult.success()) {
					teleportResult.playerUser().getUser().sendMessage(ChatUtil.prefixMessage(
							"Successfully teleported to &b" + teleportResult.waypoint().getName()));
				} else {
					teleportResult.playerUser().getUser().sendMessage(
							ChatUtil.prefixMessage("Unable to teleport to &b" + teleportResult.waypoint().getName()));
				}
			});
		});

		getArgument().addSubArgument(name);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Waypoint");
	}

}
