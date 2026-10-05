package org.luckyraven.gangland.gadget.grapple;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.timer.RepeatingTimer;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs grapple sessions: the web-shot (the vanilla hook driven by velocity at {@code Shot_Speed} blocks per tick, so
 * the client sees it fly, and raytraced over each step so it cannot tunnel through a block), then the rope swing
 * ({@link GrappleRope}) that reels the player in. Also owns the cooldown and the one-shot landing-damage grace granted
 * when an attached rope lets go. Ticked by a single shared sync {@link RepeatingTimer} (it touches entities, so never
 * async). Cooldown only (WS8-D1): no fuel/durability.
 * <p>
 * {@link #isActive(Player)} is true only while the rope is attached: a shot still in flight has not moved the player.
 * Fall immunity ({@link #isHolding(Player)}) and the landing grace need more: a rope that was taut, actually holding
 * the player, on its last tick.
 */
public class GrappleService implements BeanLifecycle {

	/** Max_Distance cap: vanilla discards a hook more than 32 blocks from its owner's feet, so keep clear of it. */
	public static final int HOOK_RANGE_LIMIT = 30;

	/** Vanilla discards a fishing hook whose squared distance to its owner's feet passes this (32 blocks). */
	private static final double VANILLA_HOOK_DISCARD_DISTANCE_SQUARED = 1024;

	/** Vanilla player air physics per tick, integrated server-side while the rope holds the player. */
	private static final double GRAVITY = 0.08;
	private static final double DRAG    = 0.98;

	private static final long MILLIS_PER_TICK = 50L;

	/** The pinned hook sits this far off the block face, so it renders on the surface rather than inside it. */
	private static final double SURFACE_OFFSET = 0.1;

	private final Map<UUID, GrappleSession> activeSessions       = new ConcurrentHashMap<>();
	private final Map<UUID, Long>           cooldownExpiryMs     = new ConcurrentHashMap<>();
	private final Map<UUID, Long>           landingGraceExpiryMs = new ConcurrentHashMap<>();

	private final JavaPlugin plugin;

	private RepeatingTimer tickTimer;

	public GrappleService(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Fires the grapple from the player's eyes along their view, driving the freshly cast vanilla {@code hook}. Returns
	 * false (the caller cancels the cast) if the player already has a session or is on cooldown. The full cooldown is
	 * applied HERE, at fire time (WS8 G3): releasing right after attaching cannot dodge it. A shot that misses is
	 * shortened to {@code Miss_Cooldown_Ticks} in {@link #cancel(Player)}.
	 */
	public boolean fire(Player player, Grapple grapple, FishHook hook) {
		UUID uuid = player.getUniqueId();
		if (activeSessions.containsKey(uuid)) return false;
		if (isOnCooldown(player)) return false;

		cooldownExpiryMs.put(uuid, System.currentTimeMillis() + grapple.getCooldownSeconds() * 1000L);

		Location eye = player.getEyeLocation();
		// The shot is driven by tickShot; vanilla's slow lobbed throw must not move the hook on its own.
		hook.setVelocity(new Vector());
		activeSessions.put(uuid, new GrappleSession(player, grapple, hook, bodyCentre(player), eye.getDirection()));
		play(grapple.getFireSound(), eye);
		return true;
	}

	/**
	 * Ends the session for any reason (arrival, timeout, release by sneak or right-click, damage, teleport, world
	 * change, death, item switch) and removes the hook. Never touches the player's velocity, so a release keeps the
	 * velocity the client already has. A rope that was taut on its last tick grants the one-shot landing grace; a slack
	 * one held nothing, so the fall still counts. A shot that never attached (miss, retract) only gets the short miss
	 * cooldown instead. A no-op without a session.
	 */
	public void cancel(Player player) {
		UUID           uuid    = player.getUniqueId();
		GrappleSession session = activeSessions.remove(uuid);
		if (session == null) return;

		session.getHook().remove();
		Grapple grapple = session.getGrapple();
		long    now     = System.currentTimeMillis();

		if (!session.isAttached()) {
			cooldownExpiryMs.put(uuid, now + grapple.getMissCooldownTicks() * MILLIS_PER_TICK);
			return;
		}
		if (!session.isTaut()) return;
		landingGraceExpiryMs.put(uuid, now + grapple.getFallDamageGraceTicks() * MILLIS_PER_TICK);
	}

	/**
	 * Fully forgets a player: drops the session (removing its hook) WITHOUT granting a landing grace — the player is
	 * gone, not landing — and clears any pending cooldown/landing-grace entries (fix round 1, F3: otherwise both maps
	 * keep one stale entry per player for the plugin's uptime). Called on quit ({@code GrappleAbortListener#onQuit}).
	 */
	public void forget(Player player) {
		UUID           uuid    = player.getUniqueId();
		GrappleSession session = activeSessions.remove(uuid);
		if (session != null) {
			session.getHook().remove();
		}
		cooldownExpiryMs.remove(uuid);
		landingGraceExpiryMs.remove(uuid);
	}

	/**
	 * True only while the rope is attached and pulling the player — not while the shot is still in flight.
	 */
	public boolean isActive(Player player) {
		GrappleSession session = activeSessions.get(player.getUniqueId());
		return session != null && session.isAttached();
	}

	/**
	 * True only while an attached rope is taut, i.e. actually holding the player up. A slack rope (falling toward an
	 * anchor below) holds nothing, so it must not forgive the fall.
	 */
	public boolean isHolding(Player player) {
		GrappleSession session = activeSessions.get(player.getUniqueId());
		return session != null && session.isAttached() && session.isTaut();
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
	 * Advances one tick of a session. Package-visible so GrappleServiceTest can drive it without the scheduler. Every
	 * path that ends the session goes through {@link #cancel(Player)} (or {@link #forget(Player)} when offline).
	 */
	void tickSession(GrappleSession session) {
		Player player = session.getPlayer();
		if (!player.isOnline()) {
			forget(player);
			return;
		}

		// Vanilla removes the hook itself when the player holds no rod in either hand, or gets over 32 blocks away.
		// It also still collides the hook with entities: once one is hooked, vanilla snaps the hook to it every tick
		// (undoing our pin), so a shot through a mob is a miss and a mob walking into the pinned hook lets go.
		FishHook hook = session.getHook();
		if (!hook.isValid() || hook.getHookedEntity() != null) {
			cancel(player);
			return;
		}

		session.setElapsedTicks(session.getElapsedTicks() + 1);
		if (session.getElapsedTicks() >= session.getGrapple().getMaxDurationTicks()) {
			cancel(player);
			return;
		}

		if (session.isAttached()) {
			tickRope(session);
		} else {
			tickShot(session);
		}
	}

	/**
	 * Moves the shot one step: raytrace from where the hook really is over this tick's step first, so a fast shot
	 * latches onto the first block in its path instead of skipping past it; otherwise give the hook this step as its
	 * velocity. Velocity, not teleport: a velocity change goes out to the client the same tick (a teleport would only
	 * show on the tracker's 5-tick position sync), so the shot is seen flying. Reaching Max_Distance without a hit is
	 * a miss.
	 */
	private void tickShot(GrappleSession session) {
		Player   player  = session.getPlayer();
		Grapple  grapple = session.getGrapple();
		World    world   = player.getWorld();
		FishHook hook    = session.getHook();
		if (!world.equals(hook.getWorld())) {
			cancel(player);
			return;
		}

		Vector position = bodyCentre(player);
		Vector moved    = position.clone().subtract(session.getLastPosition());
		session.setLastPosition(position);

		double step = Math.min(grapple.getShotSpeed(), grapple.getMaxDistance() - session.getShotTravelled());
		RayTraceResult hit = world.rayTraceBlocks(hook.getLocation(), session.getDirection(), step,
		                                          FluidCollisionMode.NEVER, true);
		if (hit != null && hit.getHitBlockFace() != null) {
			attach(session, world, hit, position, moved);
			return;
		}

		session.setShotTravelled(session.getShotTravelled() + step);
		if (session.getShotTravelled() >= grapple.getMaxDistance()) {
			cancel(player);
			return;
		}
		hook.setVelocity(session.getDirection().clone().multiply(step));
	}

	private void attach(GrappleSession session, World world, RayTraceResult hit, Vector position, Vector moved) {
		Player  player  = session.getPlayer();
		Grapple grapple = session.getGrapple();

		Vector   offset = hit.getHitBlockFace().getDirection().multiply(SURFACE_OFFSET);
		Location anchor = hit.getHitPosition().clone().add(offset).toLocation(world);

		// Outside the border, past the vanilla hook range (vanilla would delete the hook) or point-blank (it would
		// "arrive" next tick): a miss, never a free landing grace.
		if (!world.getWorldBorder().isInside(anchor)
		    || anchor.distanceSquared(player.getLocation()) > VANILLA_HOOK_DISCARD_DISTANCE_SQUARED
		    || position.distance(anchor.toVector()) <= grapple.getArrivalDistance()) {
			cancel(player);
			return;
		}

		session.attach(anchor, position, moved);
		// Send the hook the rest of the way this tick so the client sees it land; tickRope pins it from then on.
		FishHook hook = session.getHook();
		hook.setVelocity(anchor.toVector().subtract(hook.getLocation().toVector()));
		play(grapple.getAttachSound(), anchor);
	}

	/**
	 * One tick of the rope. The reel shortens it (speed ramps by Pull_Acceleration up to Reel_Speed, never below
	 * Min_Rope_Length). The player's velocity is integrated here with vanilla air physics, starting from what they
	 * were doing when the hook latched, then constrained to the rope at their measured position with
	 * {@link GrappleRope} and sent — so the swing is a real pendulum that the client cannot drift out of. A slack rope
	 * sends nothing and just tracks what the client does on its own.
	 */
	private void tickRope(GrappleSession session) {
		Player   player      = session.getPlayer();
		Grapple  grapple     = session.getGrapple();
		Location anchor      = session.getAnchor();
		World    anchorWorld = anchor.getWorld();
		// Fix round 1 (F2): World#isChunkLoaded(int, int) only checks, never loads (Location#getChunk() would).
		boolean anchorChunkLoaded = anchorWorld != null
		                            && anchorWorld.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4);
		if (anchorWorld == null || !anchorWorld.equals(player.getWorld()) || !anchorChunkLoaded) {
			cancel(player);
			return;
		}

		Vector position = bodyCentre(player);
		Vector anchorAt = anchor.toVector();
		if (position.distance(anchorAt) <= grapple.getArrivalDistance()) {
			cancel(player);
			return;
		}
		// The rope snaps if a block comes between the player and the anchor mid-swing.
		if (grapple.isRequireLineOfSight() && !hasLineOfSight(player, anchor)) {
			cancel(player);
			return;
		}

		session.setReelSpeed(Math.min(session.getReelSpeed() + grapple.getPullAcceleration(), grapple.getReelSpeed()));
		session.setRopeLength(GrappleRope.reel(session.getRopeLength(), session.getReelSpeed(),
		                                       grapple.getMinRopeLength()));

		Vector moved = position.clone().subtract(session.getLastPosition());
		session.setLastPosition(position);

		Vector velocity = collided(session.getRopeVelocity(), moved);
		velocity.setY(velocity.getY() - GRAVITY).multiply(DRAG);

		Vector constrained = GrappleRope.constrain(position, velocity, anchorAt, session.getRopeLength(),
		                                           grapple.getMaxPullSpeed());
		session.setTaut(constrained != null);
		if (constrained == null) {
			session.setRopeVelocity(moved);
		} else {
			// A taut rope is not a fall: without this, vanilla charges the whole swing as fall height on landing. A
			// slack rope holds nothing, so a fall toward an anchor below still counts.
			player.setFallDistance(0f);
			player.setVelocity(constrained);
			session.setRopeVelocity(constrained);
		}

		// Pin the hook with no velocity every tick: vanilla hook gravity would otherwise sag it off a wall or ceiling.
		FishHook hook = session.getHook();
		hook.teleport(anchor);
		hook.setVelocity(new Vector());
	}

	/**
	 * The rope velocity rebased on what the client really did: on any axis where it moved less than half of what it
	 * was sent, something stopped it (a wall, the floor), and vanilla zeroes that axis of its own motion too, so the
	 * server takes the measured movement instead of pushing on into the block.
	 */
	// ponytail: per-axis half-speed heuristic; under heavy latency it can shave a little speed at a swing's turning
	// point. Replace with a real collision query against the player's bounding box if that ever shows.
	private static Vector collided(Vector ropeVelocity, Vector moved) {
		return new Vector(collided(ropeVelocity.getX(), moved.getX()), collided(ropeVelocity.getY(), moved.getY()),
		                  collided(ropeVelocity.getZ(), moved.getZ()));
	}

	private static double collided(double sent, double moved) {
		return Math.abs(moved) < Math.abs(sent) / 2 ? moved : sent;
	}

	/** Rope maths run from the middle of the body, so a ceiling anchor straight overhead is reachable. */
	private static Vector bodyCentre(Player player) {
		return player.getLocation().add(0, player.getHeight() / 2, 0).toVector();
	}

	/**
	 * Traces from the player's eyes toward the anchor, stopping 1 block short of it so the anchor block itself never
	 * counts as an occlusion; only a block strictly between the player and the anchor does.
	 */
	private static boolean hasLineOfSight(Player player, Location anchor) {
		Location eye   = player.getEyeLocation();
		World    world = eye.getWorld();
		if (world == null) return false;

		Vector toAnchor = anchor.toVector().subtract(eye.toVector());
		double distance = toAnchor.length();
		if (distance <= 1.0) return true;

		return world.rayTraceBlocks(eye, toAnchor.normalize(), distance - 1.0, FluidCollisionMode.NEVER, true) == null;
	}

	private static void play(@Nullable SoundEffect sound, Location location) {
		if (sound != null) {
			sound.playAtLocation(location);
		}
	}

	private void tickAll() {
		for (GrappleSession session : new ArrayList<>(activeSessions.values())) {
			tickSession(session);
		}
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (tickTimer == null) {
			// RepeatingTimer(plugin, delay, period, task); start(false) = sync, required since it moves entities.
			tickTimer = new RepeatingTimer(plugin, 0L, 1L, timer -> tickAll());
			tickTimer.start(false);
		}
	}

	@Override
	public void onShutdown() {
		if (tickTimer != null) {
			tickTimer.stop();
		}
		activeSessions.values().forEach(session -> session.getHook().remove());
		activeSessions.clear();
	}
}
