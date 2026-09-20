package org.luckyraven.gangland.gadget.grapple;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.timer.RepeatingTimer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active grapple-hook pull sessions: cooldown, the per-tick pull toward the anchor, and the one-shot
 * landing-damage grace flag granted whenever a pull ends (mirrors jetpack.JetpackService's BeanLifecycle shape,
 * but ticked by a single shared org.luckyraven.keystone.timer.RepeatingTimer instead of one BukkitRunnable per
 * session — grapple pulls are short-lived and this avoids N scheduler tasks under concurrent players, WS8 §8
 * risk 3). Cooldown only (WS8-D1) — no fuel/durability.
 */
public class GrappleService implements BeanLifecycle {

	private static final long MILLIS_PER_TICK = 50L;

	private final Map<UUID, GrappleSession> activeSessions       = new ConcurrentHashMap<>();
	private final Map<UUID, Long>           cooldownExpiryMs     = new ConcurrentHashMap<>();
	private final Map<UUID, Long>           landingGraceExpiryMs = new ConcurrentHashMap<>();

	private final JavaPlugin plugin;

	private RepeatingTimer tickTimer;

	public GrappleService(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Starts a new pull toward {@code anchor}. No-op (returns false) if the player already has an active pull or
	 * is still on cooldown. Cooldown is applied HERE, at launch, not at pull-end — this is deliberate (WS8 G3):
	 * it prevents a cooldown bypass where cancelling a pull immediately after launch (damage/sneak/re-cast) would
	 * let the player re-launch instantly.
	 */
	public boolean start(Player player, Grapple grapple, Location anchor) {
		UUID uuid = player.getUniqueId();
		if (activeSessions.containsKey(uuid)) return false;
		if (isOnCooldown(player)) return false;

		cooldownExpiryMs.put(uuid, System.currentTimeMillis() + grapple.getCooldownSeconds() * 1000L);
		activeSessions.put(uuid, new GrappleSession(player, grapple, anchor));
		return true;
	}

	/**
	 * Ends the pull for any reason (arrival, timeout, damage, sneak, chunk unload, teleport, world change, death —
	 * every caller across G2/G3 routes through here) and grants the one-shot landing-damage grace flag. A no-op if
	 * the player has no active session.
	 */
	public void cancel(Player player) {
		GrappleSession session = activeSessions.remove(player.getUniqueId());
		if (session == null) return;

		long graceMs = session.getGrapple().getFallDamageGraceTicks() * MILLIS_PER_TICK;
		landingGraceExpiryMs.put(player.getUniqueId(), System.currentTimeMillis() + graceMs);
	}

	/**
	 * Fully forgets a player: drops an active session (if any) WITHOUT granting a landing grace — the player is
	 * gone, not landing, so there is nothing to protect them from — and clears any pending cooldown/landing-grace
	 * entries. Fix round 1 (F3): without this, {@code cooldownExpiryMs}/{@code landingGraceExpiryMs} would keep one
	 * stale entry per player who has ever used a grapple for the plugin's entire uptime. Called on quit (see
	 * {@code GrappleAbortListener#onQuit}, mirrors {@code CarQuitListener}/{@code JetpackActivateListener}'s own
	 * quit-cleanup precedent).
	 */
	public void forget(Player player) {
		UUID uuid = player.getUniqueId();
		activeSessions.remove(uuid);
		cooldownExpiryMs.remove(uuid);
		landingGraceExpiryMs.remove(uuid);
	}

	public boolean isActive(Player player) {
		return activeSessions.containsKey(player.getUniqueId());
	}

	public boolean isOnCooldown(Player player) {
		Long expiry = cooldownExpiryMs.get(player.getUniqueId());
		return expiry != null && expiry > System.currentTimeMillis();
	}

	@Nullable
	public GrappleSession getSession(Player player) {
		return activeSessions.get(player.getUniqueId());
	}

	/**
	 * One-shot: returns true and clears the flag if a landing grace window is still valid; returns false (and
	 * clears any stale entry) otherwise. Consumable exactly once per grant — see GrappleFallDamageListener (G3).
	 */
	public boolean consumeLandingGrace(Player player) {
		Long expiry = landingGraceExpiryMs.remove(player.getUniqueId());
		return expiry != null && expiry > System.currentTimeMillis();
	}

	/**
	 * Advances one tick of an in-progress pull: timeout check, anchor-chunk-loaded check, capped/accelerated
	 * velocity toward the anchor, and arrival auto-stop. Package-visible (not private) so GrappleServiceTest can
	 * call it directly without going through the scheduler. Every early-return path that ends the session must go
	 * through cancel(player) so the landing grace is granted consistently.
	 */
	void tickSession(GrappleSession session) {
		Player player = session.getPlayer();
		if (!player.isOnline()) {
			activeSessions.remove(player.getUniqueId());
			return;
		}

		Grapple grapple = session.getGrapple();

		session.setElapsedTicks(session.getElapsedTicks() + 1);
		if (session.getElapsedTicks() >= grapple.getMaxDurationTicks()) {
			cancel(player);
			return;
		}

		Location anchor      = session.getAnchor();
		World    anchorWorld = anchor.getWorld();
		// Fix round 1 (F2): Location#getChunk() LOADS (and generates, if needed) the chunk it returns - the old
		// !anchor.getChunk().isLoaded() branch was therefore unreachable (getChunk() always returns an already-
		// loaded chunk) and would have force-generated a far anchor's chunk every single tick. World#isChunkLoaded
		// (int, int) only checks, never loads - confirmed present at the 1.16.5 floor.
		boolean anchorChunkLoaded = anchorWorld != null
		                            && anchorWorld.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4);
		if (anchorWorld == null || !anchorWorld.equals(player.getWorld()) || !anchorChunkLoaded) {
			cancel(player);
			return;
		}

		Location playerLoc = player.getLocation();
		Vector   toAnchor   = anchor.toVector().subtract(playerLoc.toVector());
		double   distance   = toAnchor.length();

		if (distance <= grapple.getArrivalDistance()) {
			cancel(player);
			return;
		}

		Vector direction = toAnchor.normalize();
		double speed     = Math.min(session.getCurrentSpeed() + grapple.getPullAcceleration(), grapple.getMaxPullSpeed());
		session.setCurrentSpeed(speed);
		player.setVelocity(direction.multiply(speed));
	}

	private void tickAll() {
		for (GrappleSession session : new ArrayList<>(activeSessions.values())) {
			tickSession(session);
		}
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (tickTimer == null) {
			// RepeatingTimer(plugin, delay, period, task) — verified against Keystone's actual
			// org.luckyraven.keystone.timer.RepeatingTimer/Timer source (delay is param 2, period is param 3;
			// Timer's javadoc names them explicitly). delay=0 starts the pull-physics loop immediately, period=1
			// ticks it every server tick, matching JetpackTask's own runTaskTimer(plugin, 1L, 1L) cadence.
			tickTimer = new RepeatingTimer(plugin, 0L, 1L, timer -> tickAll());
			tickTimer.start(false);
		}
	}

	@Override
	public void onShutdown() {
		if (tickTimer != null) {
			tickTimer.stop();
		}
		activeSessions.clear();
	}
}
