package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.teleportation.IllegalTeleportException;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.teleportation.WaypointTeleport;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.command.CommandHandler;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.utilities.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;

@CommandHandler
public final class TeleportCommand extends Command {

	private final Map<Player, Waypoint>       reconfirm;
	private final Map<Player, CountdownTimer> reconfirmTimer;

	private final UserManager<Player> userManager;
	private final WaypointManager     waypointManager;

	public TeleportCommand(Gangland gangland,
	                       @Qualifier("online") UserManager<Player> userManager,
	                       WaypointManager waypointManager) {
		super(gangland, "teleport", true, "tp");

		reconfirm      = new HashMap<>();
		reconfirmTimer = new HashMap<>();

		this.userManager     = userManager;
		this.waypointManager = waypointManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("waypoint"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player       player   = (Player) commandSender;
		User<Player> user     = userManager.getUser(player);
		Waypoint     waypoint = waypointManager.getSelected(player);

		teleportCost(user, waypoint);
	}

	@Override
	protected void initializeArguments() {
		Argument name = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			String location = args[1];

			Waypoint waypoint = waypointManager.get(location);

			teleportCost(user, waypoint);
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			List<String> waypoints = new ArrayList<>();

			Collection<Waypoint> allWaypoints = waypointManager.getWaypoints().values();
			if (user.hasGang()) {
				int gangId = user.getGangId();

				List<String> list = allWaypoints.stream()
						.filter(waypoint -> waypoint.getGangId() == gangId)
						.map(Waypoint::getName)
						.toList();

				waypoints.addAll(list);
			}

			List<String> list = allWaypoints.stream()
					.filter(waypoint -> player.hasPermission(waypoint.getPermission()))
					.map(Waypoint::getName)
					.toList();

			waypoints.addAll(list);

			return waypoints;
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
			user.getUser().sendMessage(Messages.INVALID_WAYPOINT.toString());
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

			String teleportationCost = "The teleportation costs &a" + Settings.getMoneySymbol() +
			                           waypoint.getCost() + "&7.";
			String confirmationMessage = "&7To confirm the command re-type it again.";

			user.sendMessage(GanglandChatUtil.commandMessage(teleportationCost));
			user.sendMessage(GanglandChatUtil.color(confirmationMessage));
		} else {
			if (user.getEconomy().getAmount().compareTo(Currency.of(waypoint.getCost())) < 0) {
				user.sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
			} else {
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
			user.getUser().sendMessage(Messages.INVALID_WAYPOINT.toString());
			return;
		}

		try {
			var cooldownBypass = String.format("%s.command.%s.force_rank", Gangland.FULL_PREFIX, getLabel());
			if (user.getUser().hasPermission(cooldownBypass)) WaypointTeleport.removeCooldown(user.getUser());

			waypoint.getWaypointTeleport().teleport(getGangland(), user, (u, t) -> {
				String time    = TimeUtil.formatTime(t.getTimeLeft(), true, TimeMessages.getInstance());
				String message = Messages.WAYPOINT_TELEPORT_TIMER.toString().replace("%timer%", time);

				u.getUser().sendMessage(message);
			}).thenAccept(teleportResult -> {
				String       message;
				User<Player> user1     = teleportResult.playerUser();
				Player       player    = user1.getUser();
				Waypoint     waypoint1 = teleportResult.waypoint();

				if (teleportResult.success()) {
					if (waypoint1.getCost() != 0D) {
						BigDecimal cost = Currency.of(waypoint1.getCost());
						user1.getEconomy().withdrawAmount(cost);

						String string  = Messages.WITHDRAW_MONEY_PLAYER.toString();
						String replace = string.replace("%amount%", Settings.formatAmount(cost));

						user.sendMessage(replace);
					}

					String string = Messages.WAYPOINT_TELEPORT.toString();
					message = string.replace("%location%", waypoint1.getName());
				} else {
					String string = Messages.UNABLE_TELEPORT_WAYPOINT.toString();
					message = string.replace("%location%", waypoint1.getName());

				}

				user.sendMessage(message);
			});
		} catch (IllegalTeleportException exception) {
			CountdownTimer timer = WaypointTeleport.getCooldownTimer(user.getUser());

			if (timer == null) return;

			String time    = TimeUtil.formatTime(timer.getTimeLeft(), true, TimeMessages.getInstance());
			String string  = Messages.WAYPOINT_TELEPORT_COOLDOWN.toString();
			String message = string.replace("%timer%", time);

			user.getUser().sendMessage(message);
		}
	}

}
