package org.luckyraven.gangland.data.teleportation;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.timer.CountdownTimer;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.User;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@CustomLog
public class WaypointTeleport implements Listener {

	private static final Map<Player, CountdownTimer> teleportCooldown = new HashMap<>();
	private static final Map<Player, CountdownTimer> countdownTimer   = new HashMap<>();
	private static final Map<Player, Double>         totalDistance    = new HashMap<>();
	// the shield timer runs sync (start(false)); concurrent anyway so an off-thread caller can never corrupt it
	private static final Map<UUID, CountdownTimer>   shielded         = new ConcurrentHashMap<>();

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

		CountdownTimer timer = new CountdownTimer(plugin, waypoint.getTimer() == 0 ? 0L : 1L, waypoint.getTimer(), null,
		                                          t -> {
													  if (t.getTimeLeft() == 0) return;

													  duringTimer.accept(user, t);
												  }, t -> teleport(plugin, user, teleportResult));

		if (waypoint.getTimer() != 0) countdownTimer.put(user.getUser(), timer);
		timer.start(false);

		return teleportResult;
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

		player.sendMessage(Messages.WAYPOINT_TELEPORT_CANCELLED.toString());
	}

	/**
	 * The waypoint shield: cancels every damage a shielded player takes except void and {@code /kill}, matching what
	 * the entity {@code Invulnerable} flag used to block.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onShieldedDamage(EntityDamageEvent event) {
		if (shieldIgnores(event.getCause().name())) return;
		if (!shielded.containsKey(event.getEntity().getUniqueId())) return;

		event.setCancelled(true);
	}

	/**
	 * Compared by name: {@code KILL} (Paper 1.20.4+ {@code /kill}, damage type {@code genericKill}) does not exist in
	 * the 1.16.5 API this compiles against.
	 */
	static boolean shieldIgnores(String causeName) {
		return "VOID".equals(causeName) || "KILL".equals(causeName);
	}

	/**
	 * Docket LS-28 heal: the shield used to set the entity {@code Invulnerable} flag, which vanilla saves in
	 * {@code player.dat}, so a quit, crash, stop or reload before its timer fired left the player permanently immune
	 * to fall and mob damage ({@code /data} cannot edit players). Gangland no longer sets that flag on a player, so a
	 * player who joins with it set is carrying that leftover.
	 * <p>
	 * Deliberate migration: this clears <b>every</b> persistent {@code Invulnerable} flag on join, including one another
	 * plugin or an admin set, because the leftover cannot be told apart from those. It runs at {@code LOWEST} so a
	 * plugin that grants the flag on join re-applies it afterwards. Remove this handler once deployed servers have
	 * healed.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (!player.isInvulnerable()) return;

		player.setInvulnerable(false);
		log.info("Cleared a stale Invulnerable flag on " + player.getName() + " left by an old waypoint shield");
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

		// create a shield timer - in memory only, never the entity Invulnerable flag (docket LS-28)
		if (waypoint.getShield() != 0) {
			UUID uuid = player.getUniqueId();
			// only the timer that still owns the entry may end it, so an earlier shield never cuts a later one short
			CountdownTimer countdownTimer = new CountdownTimer(plugin, waypoint.getShield(), null, null,
			                                                   time -> shielded.remove(uuid, time));

			CountdownTimer previous = shielded.put(uuid, countdownTimer);
			if (previous != null) previous.stop();

			countdownTimer.start(false);
		}

		// remove the countdown timer when the player already teleports
		countdownTimer.remove(player);

		// successfully teleported
		TeleportResult result = new TeleportResult(true, user, waypoint);
		teleportResult.complete(result);
	}

	public record TeleportResult(boolean success, User<Player> playerUser, Waypoint waypoint) { }

}
