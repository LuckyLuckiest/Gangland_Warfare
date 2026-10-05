package org.luckyraven.gangland.gadget.grapple;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the grapple session: the fast web-shot (stepped and raytraced per tick, attaching to the first block hit,
 * missing past Max_Distance), the rope swing driven by the player's real position delta, the release paths, and the
 * cooldown / one-shot landing-grace bookkeeping. Driven through {@code tickSession} directly, never the scheduler.
 */
@DisplayName("GrappleService — web-shot, rope swing and session lifecycle")
class GrappleServiceTest {

	private static Grapple.GrappleBuilder grappleBuilder() {
		return Grapple.builder()
		              .grappleId("test")
		              .maxDistance(25)
		              .shotSpeed(5.0)
		              .maxPullSpeed(1.8)
		              .pullAcceleration(0.35)
		              .arrivalDistance(1.5)
		              .cooldownSeconds(8)
		              .missCooldownTicks(10)
		              .maxDurationTicks(100)
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

	/** An online player whose position is read from {@code where[0]}, looking along +X from 1.62 above it. */
	private static Player player(World world, Location[] where) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenAnswer(invocation -> where[0].clone());
		when(player.getEyeLocation()).thenAnswer(invocation -> {
			Location eye = where[0].clone().add(0, 1.62, 0);
			eye.setYaw(-90);
			eye.setPitch(0);
			return eye;
		});
		return player;
	}

	/** Fires, then latches the rope onto {@code anchor} from the player's current position. */
	private static GrappleSession attached(GrappleService service, Player player, Grapple grapple, Location anchor) {
		assertTrue(service.fire(player, grapple, liveHook()));
		GrappleSession session = service.getSession(player);
		session.attach(anchor, player.getLocation().toVector());
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
	@DisplayName("a shot in flight steps Shot_Speed per tick, moves the hook, and is NOT active (no fall immunity)")
	void shot_stepsHook_andIsNotActive() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		FishHook       hook    = liveHook();

		service.fire(player, grapple(), hook);
		service.tickSession(service.getSession(player));

		ArgumentCaptor<Location> moved = ArgumentCaptor.forClass(Location.class);
		verify(hook).teleport(moved.capture());
		assertEquals(5.0, moved.getValue().getX(), 1e-6);
		assertEquals(65.62, moved.getValue().getY(), 1e-6);

		assertFalse(service.isActive(player), "an unattached shot must not count as an active pull");
		verify(player, never()).setVelocity(any());
	}

	@Test
	@DisplayName("nothing within Max_Distance is a miss: hook removed, no landing grace, only the short miss cooldown")
	void shot_miss_endsWithoutGrace() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		FishHook       hook    = liveHook();
		Grapple        grapple = grappleBuilder().missCooldownTicks(0).build();

		service.fire(player, grapple, hook);
		GrappleSession session = service.getSession(player);
		for (int i = 0; i < 5; i++) {   // 25 blocks at 5 per tick
			service.tickSession(session);
		}

		assertNull(service.getSession(player));
		verify(hook).remove();
		assertFalse(service.consumeLandingGrace(player));
		assertFalse(service.isOnCooldown(player), "Miss_Cooldown_Ticks 0 replaces the full cooldown");
	}

	@Test
	@DisplayName("a block hit attaches: anchor just off the hit face, hook pinned there, rope as long as the distance")
	void shot_hit_attaches() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		FishHook       hook    = liveHook();
		hitAt(world, new Vector(12, 65.62, 0), BlockFace.WEST);

		service.fire(player, grapple(), hook);
		GrappleSession session = service.getSession(player);
		service.tickSession(session);

		assertTrue(service.isActive(player));
		Location anchor = session.getAnchor();
		assertNotNull(anchor);
		assertEquals(11.9, anchor.getX(), 1e-9);
		assertEquals(anchor.toVector().distance(new Vector(0, 64, 0)), session.getRopeLength(), 1e-9);
		verify(hook).teleport(anchor);
	}

	@Test
	@DisplayName("a hit closer than Arrival_Distance is a miss, never a free landing grace")
	void shot_pointBlankHit_isMiss() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		hitAt(world, new Vector(1, 64.5, 0), BlockFace.WEST);

		service.fire(player, grapple(), liveHook());
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

		service.fire(player, grapple(), liveHook());
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
	@DisplayName("a player held still is reeled toward the anchor, ramping by Pull_Acceleration up to Max_Pull_Speed")
	void reel_rampsThenCaps() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		Grapple        grapple = grapple();
		GrappleSession session = attached(service, player, grapple, new Location(world, 20, 64, 0));

		for (int i = 0; i < 8; i++) {
			service.tickSession(session);
		}

		ArgumentCaptor<Vector> captor = ArgumentCaptor.forClass(Vector.class);
		verify(player, times(8)).setVelocity(captor.capture());

		double previous = 0.0;
		for (Vector velocity : captor.getAllValues()) {
			assertTrue(velocity.length() <= grapple.getMaxPullSpeed() + 1e-9);
			assertTrue(velocity.length() >= previous - 1e-9);
			previous = velocity.length();

			Vector direction = velocity.clone().normalize();
			assertEquals(1.0, direction.getX(), 1e-9);
		}
		assertEquals(grapple.getMaxPullSpeed(), previous, 1e-9);
	}

	@Test
	@DisplayName("swinging: the player's sideways motion is kept (pendulum), not replaced by a beeline to the anchor")
	void swing_keepsTangentialMotion() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Location[]     where   = {new Location(world, 0, 64, 0)};
		Player         player  = player(world, where);
		GrappleSession session = attached(service, player, grapple(), new Location(world, 0, 74, 0));

		where[0] = new Location(world, 1, 64, 0);   // moved 1 block sideways since the rope latched
		service.tickSession(session);

		ArgumentCaptor<Vector> captor = ArgumentCaptor.forClass(Vector.class);
		verify(player).setVelocity(captor.capture());
		assertTrue(captor.getValue().getX() > 0.5, "sideways momentum kept, got " + captor.getValue());
		assertTrue(captor.getValue().getY() > 0, "pulled up toward the anchor, got " + captor.getValue());
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

		where[0] = new Location(world, 10, 64, 1);
		service.tickSession(session);

		assertFalse(service.isActive(player));
		assertTrue(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("release (cancel) of an attached rope keeps momentum, removes the hook, keeps the full cooldown")
	void release_keepsMomentum_oneShotGrace() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));
		World          world   = openWorld();
		Player         player  = player(world, new Location[]{new Location(world, 0, 64, 0)});
		GrappleSession session = attached(service, player, grapple(), new Location(world, 10, 64, 0));

		service.cancel(player);

		verify(player, never()).setVelocity(any());
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
