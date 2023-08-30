package me.luckyraven.data.teleportation;

import me.luckyraven.data.user.User;
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

	private static final Map<User<Player>, CountdownTimer> teleportCooldown = new HashMap<>();
	private static final Map<Player, CountdownTimer>       countdownTimer   = new HashMap<>();

	private final Waypoint waypoint;

	public WaypointTeleport(Waypoint waypoint) {
		this.waypoint = waypoint;
	}

	public static boolean userOnCooldown(User<Player> user) {
		return teleportCooldown.containsKey(user);
	}

	public static void removeCooldown(User<Player> user) {
		teleportCooldown.remove(user);
	}

	@Nullable
	public static CountdownTimer getCooldownTimer(User<Player> user) {
		return teleportCooldown.get(user);
	}

	/**
	 * Teleports the user to this waypoint.
	 *
	 * @param plugin      the plugin used
	 * @param user        the user that would be teleported
	 * @param duringTimer access the user and countdown timer during the timer
	 * @return the {@link TeleportResult} using {@link CompletableFuture} of the user
	 * @throws IllegalTeleportException when the user tries to teleport again while having a cooldown
	 */
	public CompletableFuture<TeleportResult> teleport(JavaPlugin plugin, User<Player> user,
	                                                  BiConsumer<User<Player>, CountdownTimer> duringTimer)
			throws IllegalTeleportException {
		if (userOnCooldown(user)) throw new IllegalTeleportException("Can't teleport on a cooldown");

		CompletableFuture<TeleportResult> teleportResult = new CompletableFuture<>();

		CountdownTimer timer = new CountdownTimer(plugin, waypoint.getTimer() == 0 ? 0L : 1L, waypoint.getTimer(), null,
		                                          t -> {
			                                          if (t.getTimeLeft() == 0) return;

			                                          duringTimer.accept(user, t);
		                                          }, t -> {
			World locWorld = Bukkit.getWorld(waypoint.getWorld());

			// if locWorld was a valid world
			if (locWorld != null) {
				Location location = new Location(locWorld, waypoint.getX(), waypoint.getY(), waypoint.getZ(),
				                                 waypoint.getYaw(), waypoint.getPitch());

				TeleportEvent event = new TeleportEvent(waypoint, user);
				Bukkit.getPluginManager().callEvent(event);

				if (!event.isCancelled()) {
					user.getUser().teleport(location);

					// create a cooldown
					if (waypoint.getCooldown() != 0) {
						CountdownTimer countdownTimer = new CountdownTimer(plugin, waypoint.getCooldown(), null, null,
						                                                   cooldownTimer -> teleportCooldown.remove(
								                                                   user));
						teleportCooldown.put(user, countdownTimer);

						countdownTimer.start();
					}

					// successfully teleported
					TeleportResult result = new TeleportResult(true, user, waypoint);
					teleportResult.complete(result);
				}
				// event cancelled teleportation
				else {
					TeleportResult result = new TeleportResult(false, user, waypoint);
					teleportResult.complete(result);
				}
			}
			// when locWorld was not valid
			else {
				TeleportResult result = new TeleportResult(false, user, waypoint);
				teleportResult.complete(result);
			}
		});

		if (waypoint.getTimer() != 0) countdownTimer.put(user.getUser(), timer);
		timer.start();

		return teleportResult;
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();

		if (!countdownTimer.containsKey(player)) return;

		CountdownTimer timer = countdownTimer.get(player);
		timer.cancel();
		countdownTimer.remove(player);

		player.sendMessage("Cancelled the timer!");
	}

	public record TeleportResult(boolean success, User<Player> playerUser, Waypoint waypoint) {}

}
