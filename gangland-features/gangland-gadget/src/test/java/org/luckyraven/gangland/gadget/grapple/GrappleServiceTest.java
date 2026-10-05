package org.luckyraven.gangland.gadget.grapple;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the grapple session: the velocity-driven web-shot (raytraced from the hook each tick, attaching to the first
 * block hit, missing past Max_Distance), the server-integrated rope swing, the release paths, and the cooldown /
 * one-shot landing-grace bookkeeping. Driven through {@code tickSession} directly, never the scheduler.
 */
@DisplayName("GrappleService — web-shot, rope swing and session lifecycle")
class GrappleServiceTest {

	private static final double BODY_HALF_HEIGHT = 0.9;

	private static Grapple.GrappleBuilder grappleBuilder() {
		return Grapple.builder()
		              .grappleId("test")
		              .maxDistance(25)
		              .shotSpeed(3.9)
		              .maxPullSpeed(1.8)
		              .pullAcceleration(0.05)
		              .reelSpeed(0.3)
		              .minRopeLength(3.0)
		              .arrivalDistance(3.5)
		              .cooldownSeconds(8)
		              .missCooldownTicks(10)
		              .maxDurationTicks(70)
		              .fallDamageGraceTicks(40)
		              .requireLineOfSight(false);
	}

	private static Grapple grapple() {
		return grappleBuilder().build();
	}

	/** A world with every chunk loaded, everything inside the border and nothing to hit unless stubbed. */
	private static World openWorld() {
		World       world  = mock(World.class);
		WorldBorder border = mock(WorldBorder.class);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
		when(world.getWorldBorder()).thenReturn(border);
		when(border.isInside(any(Location.class))).thenReturn(true);
		return world;
	}

	private static void hitAt(World world, Vector hitPosition, BlockFace face) {
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class),
		                          anyBoolean())).thenReturn(new RayTraceResult(hitPosition, face));
	}

	private static FishHook liveHook() {
		FishHook hook = mock(FishHook.class);
		when(hook.isValid()).thenReturn(true);
		return hook;
	}

	/**
	 * A live hook in {@code world} starting at {@code start}. Like the vanilla FLYING hook, a velocity set on it moves
	 * it by that much (the move vanilla makes after the scheduler ran); a teleport puts it straight there.
	 */
	private static FishHook flyingHook(World world, Location start) {
		Location[] at   = {start.clone()};
		FishHook   hook = mock(FishHook.class);
		when(hook.isValid()).thenReturn(true);
		when(hook.getWorld()).thenReturn(world);
		when(hook.getLocation()).thenAnswer(invocation -> at[0].clone());
		doAnswer(invocation -> at[0].add(invocation.<Vector>getArgument(0))).when(hook).setVelocity(any(Vector.class));
		when(hook.teleport(any(Location.class))).thenAnswer(invocation -> {
			at[0] = invocation.<Location>getArgument(0).clone();
			return true;
		});
		return hook;
	}

	/** An online 1.8-tall player whose feet are read from {@code where[0]}, looking along +X from 1.62 above them. */
	private static Player player(World world, Location[] where) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getHeight()).thenReturn(BODY_HALF_HEIGHT * 2);
		when(player.getLocation()).thenAnswer(invocation -> where[0].clone());
		when(player.getEyeLocation()).thenAnswer(invocation -> {
			Location eye = where[0].clone().add(0, 1.62, 0);
			eye.setYaw(-90);
			eye.setPitch(0);
			return eye;
		});
		return player;
	}

	private static Vector bodyCentre(Player player) {
		return player.getLocation().add(0, BODY_HALF_HEIGHT, 0).toVector();
	}

	/** Fires, then latches the rope onto {@code anchor} from the player's current position, at rest. */
	private static GrappleSession attached(GrappleService service, Player player, Grapple grapple, Location anchor) {
		assertTrue(service.fire(player, grapple, liveHook()));
		GrappleSession session = service.getSession(player);
		session.attach(anchor, bodyCentre(player), new Vector());
		return session;
	}

	@Test
	@DisplayName("fire() succeeds once; a second fire() while a shot is in flight is rejected and keeps the session")
	void fire_whileInFlight_rejected() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		FishHook       hook    = liveHook();

		assertTrue(service.fire(player, grapple(), hook));
		assertFalse(service.fire(player, grapple(), liveHook()));
		assertSame(hook, service.getSession(player).getHook());
		verify(hook).setVelocity(new Vector());
	}

	@Test
	@DisplayName("G1: the shot is driven by velocity and raytraced from the hook's own position, step by step")
	void shot_velocityDriven_raytracedFromHook() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		// Vanilla spawns the hook a little off the eye; the trace must start where the hook really is.
		FishHook       hook    = flyingHook(world, new Location(world, 0.3, 65.5, 0.2));
		Grapple        grapple = grappleBuilder().maxDistance(10).build();

		service.fire(player, grapple, hook);
		GrappleSession session = service.getSession(player);
		for (int i = 0; i < 3; i++) {   // 3.9 + 3.9 + a shortened 2.2: Max_Distance 10 reached, a miss
			service.tickSession(session);
		}

		ArgumentCaptor<Location> origins = ArgumentCaptor.forClass(Location.class);
		ArgumentCaptor<Vector>   dirs    = ArgumentCaptor.forClass(Vector.class);
		ArgumentCaptor<Double>   lengths = ArgumentCaptor.forClass(Double.class);
		verify(world, times(3)).rayTraceBlocks(origins.capture(), dirs.capture(), lengths.capture(),
		                                       eq(FluidCollisionMode.NEVER), eq(true));
		List<Location> from = origins.getAllValues();
		assertEquals(0.3, from.get(0).getX(), 1e-9);
		assertEquals(65.5, from.get(0).getY(), 1e-9);
		assertEquals(0.2, from.get(0).getZ(), 1e-9);
		assertEquals(4.2, from.get(1).getX(), 1e-9, "the second trace starts one step further on");
		assertEquals(8.1, from.get(2).getX(), 1e-9);
		assertEquals(List.of(3.9, 3.9, 10 - 7.8), lengths.getAllValues().stream()
		                                                 .map(length -> Math.round(length * 1e9) / 1e9).toList());
		assertEquals(1.0, dirs.getValue().getX(), 1e-9);

		verify(hook, times(2)).setVelocity(new Vector(3.9, 0, 0));
		verify(hook, never()).teleport(any(Location.class));
		assertNull(service.getSession(player), "Max_Distance reached without a hit is a miss");
		assertFalse(service.isActive(player));
		verify(player, never()).setVelocity(any());
	}

	@Test
	@DisplayName("G9: a hook that ended up in another world than its owner ends the shot")
	void shot_hookInOtherWorld_cancels() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		World          other   = openWorld();
		FishHook       hook    = flyingHook(other, new Location(other, 0, 65.62, 0));

		service.fire(player, grapple(), hook);
		service.tickSession(service.getSession(player));

		assertNull(service.getSession(player));
		verify(hook).remove();
	}

	@Test
	@DisplayName("a shot that vanilla hooked onto an entity in flight is a miss, not a rope to a hidden anchor")
	void shot_entityHookedInFlight_isMiss() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		FishHook       hook    = flyingHook(world, new Location(world, 0, 65.62, 0));
		hitAt(world, new Vector(12, 65.62, 0), BlockFace.WEST);

		service.fire(player, grapple(), hook);
		when(hook.getHookedEntity()).thenReturn(mock(Entity.class));
		service.tickSession(service.getSession(player));

		assertNull(service.getSession(player));
		verify(hook).remove();
		assertFalse(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("an entity walking into the pinned hook mid-swing lets the rope go")
	void rope_entityHookedOnPinnedHook_letsGo() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 20, 64, 0));
		when(session.getHook().getHookedEntity()).thenReturn(mock(Entity.class));

		service.tickSession(session);

		assertFalse(service.isActive(player));
		verify(session.getHook()).remove();
		verify(player, never()).setVelocity(any());
	}

	@Test
	@DisplayName("nothing within Max_Distance is a miss: hook removed, no landing grace, only the short miss cooldown")
	void shot_miss_endsWithoutGrace() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		FishHook       hook    = flyingHook(world, new Location(world, 0, 65.62, 0));
		Grapple        grapple = grappleBuilder().missCooldownTicks(0).build();

		service.fire(player, grapple, hook);
		GrappleSession session = service.getSession(player);
		for (int i = 0; i < 7; i++) {   // 25 blocks at 3.9 per tick
			service.tickSession(session);
		}

		assertNull(service.getSession(player));
		verify(hook).remove();
		assertFalse(service.consumeLandingGrace(player));
		assertFalse(service.isOnCooldown(player), "Miss_Cooldown_Ticks 0 replaces the full cooldown");
	}

	@Test
	@DisplayName("a block hit attaches: anchor just off the hit face, hook sent onto it, rope measured from the body")
	void shot_hit_attaches() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		FishHook       hook    = flyingHook(world, new Location(world, 0, 65.62, 0));
		hitAt(world, new Vector(12, 65.62, 0), BlockFace.WEST);

		service.fire(player, grapple(), hook);
		GrappleSession session = service.getSession(player);
		service.tickSession(session);

		assertTrue(service.isActive(player));
		Location anchor = session.getAnchor();
		assertNotNull(anchor);
		assertEquals(11.9, anchor.getX(), 1e-9);
		assertEquals(anchor.toVector().distance(new Vector(0, 64.9, 0)), session.getRopeLength(), 1e-9);
		assertEquals(11.9, hook.getLocation().getX(), 1e-9, "the hook visibly flies onto the anchor this tick");
	}

	@Test
	@DisplayName("a hit closer than Arrival_Distance (from the body centre) is a miss, never a free landing grace")
	void shot_pointBlankHit_isMiss() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		hitAt(world, new Vector(1, 64.5, 0), BlockFace.WEST);

		service.fire(player, grapple(), flyingHook(world, new Location(world, 0, 65.62, 0)));
		service.tickSession(service.getSession(player));

		assertNull(service.getSession(player));
		assertFalse(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("G8: an anchor more than 32 blocks from the player's feet is a miss (vanilla would discard the hook)")
	void shot_hitPastVanillaHookRange_isMiss() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		hitAt(world, new Vector(32.5, 64, 0), BlockFace.WEST);

		service.fire(player, grapple(), flyingHook(world, new Location(world, 0, 65.62, 0)));
		service.tickSession(service.getSession(player));

		assertNull(service.getSession(player));
		assertFalse(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("a hit outside the world border is a miss")
	void shot_outsideBorder_isMiss() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		when(world.getWorldBorder().isInside(any(Location.class))).thenReturn(false);
		Player player = player(world, new Location[]{new Location(world, 0, 64, 0)});
		hitAt(world, new Vector(12, 65.62, 0), BlockFace.WEST);

		service.fire(player, grapple(), flyingHook(world, new Location(world, 0, 65.62, 0)));
		service.tickSession(service.getSession(player));

		assertNull(service.getSession(player));
	}

	@Test
	@DisplayName("vanilla removing the hook (item switched away, owner too far) ends the session")
	void hookGone_endsSession() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 10, 64, 0));
		when(session.getHook().isValid()).thenReturn(false);

		service.tickSession(session);

		assertFalse(service.isActive(player));
	}

	@Test
	@DisplayName("the session ends once elapsedTicks reaches Max_Duration_Ticks")
	void timeout_endsSession() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grappleBuilder().maxDurationTicks(3).build(),
		                                  new Location(world, 30, 64, 0));

		for (int i = 0; i < 3; i++) {
			service.tickSession(session);
		}

		assertFalse(service.isActive(player));
	}

	@Test
	@DisplayName("the rope lets go when the anchor's chunk is unloaded (World#isChunkLoaded, F2)")
	void chunkUnloaded_endsSession() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 20, 64, 0));
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

		service.tickSession(session);

		assertFalse(service.isActive(player));
	}

	@Test
	@DisplayName("G2: an attached hook is pinned on the anchor with zero velocity every tick (no sag, no pop)")
	void attached_hookPinnedWithZeroVelocity() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		Location       anchor  = new Location(world, 20, 64, 0);
		GrappleSession session = attached(service, player, grapple(), anchor);

		service.tickSession(session);
		service.tickSession(session);

		verify(session.getHook(), times(2)).teleport(anchor);
		// once at fire (no vanilla lob), then once per attached tick
		verify(session.getHook(), times(3)).setVelocity(new Vector());
	}

	@Test
	@DisplayName("G7: a taut rope is not a fall: fall distance is reset every attached tick")
	void attached_resetsFallDistance() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 20, 64, 0));

		service.tickSession(session);
		service.tickSession(session);

		verify(player, times(2)).setFallDistance(0f);
	}

	@Test
	@DisplayName("G4: the reel ramps by Pull_Acceleration up to Reel_Speed, and stops at Min_Rope_Length")
	void reel_rampsToReelSpeed_floorsAtMinRopeLength() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		Grapple        grapple = grapple();
		GrappleSession session = attached(service, player, grapple, new Location(world, 0, 69.9, 0));   // 5 above

		service.tickSession(session);
		assertEquals(0.05, session.getReelSpeed(), 1e-9);
		assertEquals(4.95, session.getRopeLength(), 1e-9);

		for (int i = 0; i < 10; i++) {
			service.tickSession(session);
		}
		assertEquals(grapple.getReelSpeed(), session.getReelSpeed(), 1e-9, "capped at Reel_Speed, not Max_Pull_Speed");
		assertEquals(grapple.getMinRopeLength(), session.getRopeLength(), 1e-9);
	}

	@Test
	@DisplayName("G3: a 20-block shot and rope (shipped defaults) swing the player under the anchor and arrive in time")
	void swing_multiTick_passesUnderAnchorThenArrives() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 64, 0)};
		Player         player  = player(world, where);
		Vector[]       sent    = {null};
		doAnswer(invocation -> sent[0] = invocation.<Vector>getArgument(0).clone()).when(player)
		                                                                           .setVelocity(any(Vector.class));
		// Every knob at its grapples.yml default, line of sight included.
		Grapple  grapple = grappleBuilder().requireLineOfSight(true).build();
		Location anchor  = new Location(world, 14, 79, 0);   // ~20 blocks up and ahead
		// Five clear 3.9-block shot steps, the sixth hits; every later trace (the line-of-sight checks) is clear.
		RayTraceResult hit = new RayTraceResult(new Vector(14.1, 79, 0), BlockFace.WEST);
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class),
		                          anyBoolean())).thenReturn(null, null, null, null, null, hit, null);

		assertTrue(service.fire(player, grapple, flyingHook(world, new Location(world, 0, 65.62, 0))));
		GrappleSession session = service.getSession(player);

		Vector  client      = new Vector();
		boolean passedUnder = false;
		double  speedUnder  = 0;
		int     ticks       = 0;
		int     shotTicks   = 0;
		while (service.getSession(player) != null && ticks < 200) {
			boolean flying = !session.isAttached();
			sent[0] = null;
			service.tickSession(session);
			ticks++;
			if (service.getSession(player) == null) break;
			if (flying) {
				shotTicks++;   // standing on the ground until the hook lands
				continue;
			}

			// The client: a velocity packet replaces its motion, then it moves and applies its own air physics.
			if (sent[0] != null) {
				assertTrue(Double.isFinite(sent[0].lengthSquared()), "never NaN");
				client = sent[0].clone();
			}
			double xBefore = where[0].getX();
			where[0].add(client);
			if (!passedUnder && xBefore < anchor.getX() && where[0].getX() >= anchor.getX()) {
				passedUnder = true;
				speedUnder  = Math.hypot(client.getX(), client.getZ());
				assertTrue(where[0].getY() < anchor.getY() - 2, "under the anchor, got " + where[0]);
			}
			client.setY(client.getY() - 0.08).multiply(0.98);
		}

		assertTrue(passedUnder, "the rope must swing the player under the anchor, not beeline at it");
		assertTrue(speedUnder > 0.4, "tangential speed survives the swing, got " + speedUnder);
		assertEquals(6, shotTicks, "the 20-block shot flies for six ticks of the shared budget");
		assertTrue(ticks < grapple.getMaxDurationTicks(), "shot plus swing arrive before the timeout, took " + ticks);
		assertTrue(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("G3: the rope velocity is integrated server-side (gravity 0.08, drag 0.98), not read off the position")
	void rope_integratesGravityServerSide() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 64, 0)};
		Player         player  = player(world, where);
		Vector[]       sent    = {null};
		// The client moves exactly as told, like a real one with nothing in the way.
		doAnswer(invocation -> {
			sent[0] = invocation.<Vector>getArgument(0).clone();
			where[0].add(sent[0]);
			return null;
		}).when(player).setVelocity(any(Vector.class));
		// A level 10-block rope from a standing start.
		GrappleSession session = attached(service, player, grapple(), new Location(world, 10, 64.9, 0));

		service.tickSession(session);
		Vector first = sent[0];
		assertEquals(0.05, first.getX(), 1e-9, "only the reeled excess pulls inward");
		assertEquals(-0.08 * 0.98, first.getY(), 1e-9, "gravity is the server's job while the rope holds you");

		service.tickSession(session);
		Vector second = sent[0];
		assertEquals(0.1493, second.getX(), 1e-3);
		assertEquals(-0.1544, second.getY(), 1e-3, "the fall keeps building, tick on tick");
	}

	@Test
	@DisplayName("G3: the rope velocity is seeded from the player's measured movement when the hook latches on")
	void rope_seededFromMeasuredDeltaAtAttach() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 64, 0)};
		Player         player  = player(world, where);
		hitAt(world, new Vector(0.5, 75, 0), BlockFace.DOWN);   // 10 blocks straight overhead

		service.fire(player, grapple(), flyingHook(world, new Location(world, 0, 65.62, 0)));
		GrappleSession session = service.getSession(player);
		where[0] = new Location(world, 0.5, 64, 0);   // running at 0.5 b/t when the hook hits
		service.tickSession(session);
		where[0] = new Location(world, 1.0, 64, 0);   // and still running on the first rope tick
		service.tickSession(session);

		ArgumentCaptor<Vector> sent = ArgumentCaptor.forClass(Vector.class);
		verify(player).setVelocity(sent.capture());
		assertTrue(sent.getValue().getX() > 0.4, "the run-up carries into the swing, got " + sent.getValue());
	}

	@Test
	@DisplayName("a client stopped by a wall loses that axis of the rope velocity instead of being pushed into it")
	void rope_clientBlockedOnAxis_velocityCollapses() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 0, 74.9, 0));
		// The rope had the player swinging at 1.5 b/t along x, but the client did not move: it hit a wall.
		session.setRopeVelocity(new Vector(1.5, 0, 0));

		service.tickSession(session);

		ArgumentCaptor<Vector> sent = ArgumentCaptor.forClass(Vector.class);
		verify(player).setVelocity(sent.capture());
		assertEquals(0, sent.getValue().getX(), 1e-9, "no phantom push into the wall, got " + sent.getValue());
	}

	@Test
	@DisplayName("a slack rope (player closer than the rope length) applies no force at all")
	void slackRope_noVelocity() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 64, 0)};
		Player         player  = player(world, where);
		GrappleSession session = attached(service, player, grapple(), new Location(world, 10, 64, 0));

		where[0] = new Location(world, 3, 64, 0);   // closed 3 blocks in one tick, faster than the reel
		service.tickSession(session);

		assertTrue(service.isActive(player));
		verify(player, never()).setVelocity(any());
		verify(player, never()).setFallDistance(anyFloat());
	}

	@Test
	@DisplayName("G7: falling toward an anchor below never pulls the rope taut, so the fall still counts")
	void fallingTowardAnchorBelow_fallDistanceBuilds() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 90, 0)};
		Player         player  = player(world, where);
		GrappleSession session = attached(service, player, grapple(), new Location(world, 0, 64.1, 0));

		for (int i = 0; i < 5; i++) {
			where[0].add(0, -1.5, 0);   // free fall, faster than the reel
			service.tickSession(session);
		}

		assertTrue(service.isActive(player));
		assertFalse(service.isHolding(player), "a slack rope holds nothing, so fall damage is not cancelled");
		verify(player, never()).setFallDistance(anyFloat());
	}

	@Test
	@DisplayName("a slack rope that reaches Arrival_Distance (a fall onto an anchor below) grants no landing grace")
	void slackArrival_noLandingGrace() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 90, 0)};
		Player         player  = player(world, where);
		GrappleSession session = attached(service, player, grapple(), new Location(world, 0, 64.1, 0));

		for (int i = 0; i < 30 && service.getSession(player) != null; i++) {
			where[0].add(0, -1.5, 0);   // a lethal free fall the whole way down to the anchor
			service.tickSession(session);
		}

		assertNull(service.getSession(player), "arrival ends the session");
		assertFalse(service.consumeLandingGrace(player), "the rope never held the fall, so the landing still hurts");
	}

	@Test
	@DisplayName("isHolding is true only after a tick on which the rope was taut")
	void isHolding_onlyWhileTaut() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 10, 64.9, 0));

		assertFalse(service.isHolding(player), "not until the rope has actually held the player");
		service.tickSession(session);
		assertTrue(service.isHolding(player));
	}

	@Test
	@DisplayName("Require_Line_Of_Sight: a block between player and anchor mid-swing snaps the rope")
	void lineOfSightBlocked_snapsRope() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 64, 0)};
		Player         player  = player(world, where);
		GrappleSession session = attached(service, player, grappleBuilder().requireLineOfSight(true).build(),
		                                  new Location(world, 20, 64, 0));
		hitAt(world, new Vector(10, 65, 0), BlockFace.WEST);

		service.tickSession(session);

		assertFalse(service.isActive(player));
	}

	@Test
	@DisplayName("arrival lets go and grants the one-shot landing grace")
	void arrival_endsAndGrantsGrace() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 64, 0)};
		Player         player  = player(world, where);
		GrappleSession session = attached(service, player, grapple(), new Location(world, 10, 64, 0));
		service.tickSession(session);   // the rope takes the strain

		where[0] = new Location(world, 10, 64, 1);
		service.tickSession(session);

		assertFalse(service.isActive(player));
		assertTrue(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("G6: arrival under a ceiling anchor is measured from the body centre, not the feet")
	void arrival_underCeiling_fromBodyCentre() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 64, 0)};
		Player         player  = player(world, where);
		GrappleSession session = attached(service, player, grappleBuilder().arrivalDistance(1.5).build(),
		                                  new Location(world, 0, 67.9, 0));
		service.tickSession(session);   // the rope takes the strain

		where[0] = new Location(world, 0, 65.9, 0);   // feet 2.0 below the anchor, body centre 1.1
		service.tickSession(session);

		assertFalse(service.isActive(player));
		assertTrue(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("release (cancel) of a taut rope keeps momentum, removes the hook, keeps the full cooldown")
	void release_keepsMomentum_oneShotGrace() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 10, 64, 0));
		service.tickSession(session);   // the rope takes the strain
		clearInvocations(player);

		service.cancel(player);

		verify(player, never()).setVelocity(any());
		verify(player, never()).getVelocity();
		verify(session.getHook()).remove();
		assertTrue(service.isOnCooldown(player));
		assertTrue(service.consumeLandingGrace(player));
		assertFalse(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("forget() drops the session (removing the hook), cooldown and landing grace immediately (F3)")
	void forget_quitMidPull_clearsEverything() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 10, 64, 0));

		service.forget(player);

		assertFalse(service.isActive(player));
		verify(session.getHook()).remove();
		assertFalse(service.isOnCooldown(player));
		assertFalse(service.consumeLandingGrace(player), "a quit is not a landing");
		assertTrue(service.fire(player, grapple(), liveHook()));
	}
}
