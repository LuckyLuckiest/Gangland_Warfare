package me.luckyraven.data.teleportation;

import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class WaypointTeleport implements Listener {

	private static final Map<Player, CountdownTimer> teleportCooldown = new HashMap<>();
	private static final Map<Player, CountdownTimer> countdownTimer   = new HashMap<>();
	private static final Map<Player, Double>         totalDistance    = new HashMap<>();

	private final Waypoint waypoint;

	public WaypointTeleport(Waypoint waypoint) {
		this.waypoint = waypoint;
	}

	public static boolean userOnCooldown(Player player) {
		return teleportCooldown.containsKey(player);
	}

	public static void removeCooldown(Player player) {
		teleportCooldown.remove(player);
	}

	@Nullable
	public static CountdownTimer getCooldownTimer(Player player) {
		return teleportCooldown.get(player);
	}

	/**
	 * Teleports the user to this waypoint.
	 *
	 * @param plugin the plugin used
	 * @param user the user that would be teleported
	 * @param duringTimer access the user and countdown timer during the timer
	 *
	 * @return the {@link TeleportResult} using {@link CompletableFuture} of the user
	 *
	 * @throws IllegalTeleportException when the user tries to teleport again while having a cooldown
	 */
	public CompletableFuture<TeleportResult> teleport(JavaPlugin plugin, User<Player> user,
													  BiConsumer<User<Player>, CountdownTimer> duringTimer) throws
			IllegalTeleportException {
		if (userOnCooldown(user.getUser())) throw new IllegalTeleportException("Can't teleport on a cooldown");

		CompletableFuture<TeleportResult> teleportResult = new CompletableFuture<>();

		CountdownTimer timer =
				new CountdownTimer(plugin, waypoint.getTimer() == 0 ? 0L : 1L, waypoint.getTimer(), null, t -> {
					if (t.getTimeLeft() == 0) return;

					duringTimer.accept(user, t);
				}, t -> teleport(plugin, user, teleportResult));

		if (waypoint.getTimer() != 0) countdownTimer.put(user.getUser(), timer);
		timer.start(false);

		return teleportResult;
	}

	private void teleport(JavaPlugin plugin, User<Player> user, CompletableFuture<TeleportResult> teleportResult) {
		World locWorld = Bukkit.getWorld(waypoint.getWorld());

		// if locWorld was not valid
		if (locWorld == null) {
			TeleportResult result = new TeleportResult(false, user, waypoint);
			teleportResult.complete(result);
			return;
		}

		// if locWorld was a valid world
		Player player = user.getUser();
		Location location = new Location(locWorld, waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getYaw(),
										 waypoint.getPitch());

		TeleportEvent event = new TeleportEvent(user, player.getLocation(), waypoint);
		Bukkit.getPluginManager().callEvent(event);

		// event cancelled teleportation
		if (event.isCancelled()) {
			TeleportResult result = new TeleportResult(false, user, waypoint);
			teleportResult.complete(result);
			return;
		}

		player.teleport(location);

		// create a cooldown timer
		if (waypoint.getCooldown() != 0) {
			CountdownTimer countdownTimer = new CountdownTimer(plugin, waypoint.getCooldown(), null, null,
															   time -> teleportCooldown.remove(player));
			teleportCooldown.put(player, countdownTimer);

			countdownTimer.start(true);
		}

		// create a shield timer
		if (waypoint.getShield() != 0) {
			CountdownTimer countdownTimer =
					new CountdownTimer(plugin, waypoint.getShield(), null, null, time -> player.setInvulnerable(false));

			player.setInvulnerable(true);

			countdownTimer.start(false);
		}

		// remove the countdown timer when the player already teleports
		countdownTimer.remove(player);

		// successfully teleported
		TeleportResult result = new TeleportResult(true, user, waypoint);
		teleportResult.complete(result);
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player   player = event.getPlayer();
		Location from   = event.getFrom();
		Location to     = event.getTo();

		if (to == null || !countdownTimer.containsKey(player)) return;

		double deltaX     = Math.abs(to.getX() - from.getX());
		double deltaY     = Math.abs(to.getY() - from.getY());
		double deltaZ     = Math.abs(to.getZ() - from.getZ());
		double totalDelta = deltaX + deltaY + deltaZ;

		if (!totalDistance.containsKey(player)) {
			totalDistance.put(player, totalDelta);
			return;
		}

		double currentTotalDelta = totalDistance.get(player) + totalDelta;
		totalDistance.put(player, currentTotalDelta);

		double threshold = 1.5; // number of blocks
		// when the player moves less than the threshold, ignore the case
		if (currentTotalDelta < threshold) return;

		CountdownTimer timer = countdownTimer.get(player);
		timer.cancel();
		countdownTimer.remove(player);
		totalDistance.remove(player);

		player.sendMessage(MessageAddon.WAYPOINT_TELEPORT_CANCELLED.toString());
	}

	public record TeleportResult(boolean success, User<Player> playerUser, Waypoint waypoint) { }

}
