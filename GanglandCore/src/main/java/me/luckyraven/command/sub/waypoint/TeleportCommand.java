package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.teleportation.IllegalTeleportException;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.teleportation.WaypointTeleport;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TeleportCommand extends CommandHandler {

	private final Map<Player, Waypoint>       reconfirm;
	private final Map<Player, CountdownTimer> reconfirmTimer;

	public TeleportCommand(Gangland gangland) {
		super(gangland, "teleport", true, "tp");

		reconfirm      = new HashMap<>();
		reconfirmTimer = new HashMap<>();

		List<CommandInformation> list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("waypoint"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		UserManager<Player> userManager     = getGangland().getInitializer().getUserManager();
		WaypointManager     waypointManager = getGangland().getInitializer().getWaypointManager();

		Player       player   = (Player) commandSender;
		User<Player> user     = userManager.getUser(player);
		Waypoint     waypoint = waypointManager.getSelected(player);

		teleportCost(user, waypoint);
	}

	@Override
	protected void initializeArguments() {
		UserManager<Player> userManager     = getGangland().getInitializer().getUserManager();
		WaypointManager     waypointManager = getGangland().getInitializer().getWaypointManager();

		Argument name = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			Player       player   = (Player) sender;
			User<Player> user     = userManager.getUser(player);
			Waypoint     waypoint = waypointManager.get(args[1]);

			teleportCost(user, waypoint);
		});

		getArgument().addPermission(getPermission() + ".cooldown_bypass");

		getArgument().addSubArgument(name);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Waypoint");
	}

	private void teleportCost(User<Player> user, Waypoint waypoint) {
		if (waypoint == null) {
			user.getUser().sendMessage(MessageAddon.INVALID_WAYPOINT.toString());
			return;
		}

		Player player = user.getUser();

		if (waypoint.getCost() != 0D && !reconfirm.containsKey(player)) {
			reconfirm.put(player, waypoint);

			CountdownTimer timer = new CountdownTimer(getGangland(), 30, null, null, t -> {
				reconfirm.remove(player);
			});
			reconfirmTimer.put(player, timer);

			timer.start(true);

			player.sendMessage(ChatUtil.commandMessage(
					"The teleportation costs &a" + SettingAddon.getMoneySymbol() + waypoint.getCost() + "&7."));
			player.sendMessage(ChatUtil.color("&7To confirm the command re-type it again."));
		} else {
			if (user.getEconomy().getBalance() < waypoint.getCost()) player.sendMessage(
					MessageAddon.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
			else {
				reconfirm.remove(player);
				CountdownTimer timer = reconfirmTimer.get(player);

				if (timer != null) {
					if (!timer.isCancelled()) timer.cancel();
					reconfirmTimer.remove(player);
				}

				teleport(user, waypoint);
			}
		}
	}

	private void teleport(User<Player> user, Waypoint waypoint) {
		if (waypoint == null) {
			user.getUser().sendMessage(MessageAddon.INVALID_WAYPOINT.toString());
			return;
		}

		try {
			if (user.getUser()
					.hasPermission("gangland.command.teleport.cooldown_bypass")) WaypointTeleport.removeCooldown(
					user.getUser());

			waypoint.getWaypointTeleport().teleport(getGangland(), user, (u, t) -> {
				String time    = TimeUtil.formatTime(t.getTimeLeft(), true);
				String message = MessageAddon.WAYPOINT_TELEPORT_TIMER.toString().replace("%timer%", time);

				u.getUser().sendMessage(message);
			}).thenAccept(teleportResult -> {
				String       message;
				User<Player> user1     = teleportResult.playerUser();
				Player       player    = user1.getUser();
				Waypoint     waypoint1 = teleportResult.waypoint();

				if (teleportResult.success()) {
					if (waypoint1.getCost() != 0D) {
						user1.getEconomy().withdraw(waypoint1.getCost());
						player.sendMessage(MessageAddon.WITHDRAW_MONEY_PLAYER.toString()
																			 .replace("%amount%",
																					  SettingAddon.formatDouble(
																							  waypoint1.getCost())));
					}
					message = MessageAddon.WAYPOINT_TELEPORT.toString().replace("%location%", waypoint1.getName());

				} else {
					message = MessageAddon.UNABLE_TELEPORT_WAYPOINT.toString()
																   .replace("%location%", waypoint1.getName());

				}
				player.sendMessage(message);
			});
		} catch (IllegalTeleportException exception) {
			CountdownTimer timer = WaypointTeleport.getCooldownTimer(user.getUser());

			if (timer == null) return;

			String time    = TimeUtil.formatTime(timer.getTimeLeft(), true);
			String message = MessageAddon.WAYPOINT_TELEPORT_COOLDOWN.toString().replace("%timer%", time);

			user.getUser().sendMessage(message);
		}
	}

}
