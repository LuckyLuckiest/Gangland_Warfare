package org.luckyraven.gangland.gadget.grapple;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins WS8 G2+G3 (+ fix round 1): {@link GrappleService}'s cooldown/session bookkeeping, the {@code tickSession}
 * per-tick pull physics, and {@code forget}'s quit-cleanup, exercised directly (never through {@code onInitialize}/
 * the scheduler) so this stays a pure-logic suite.
 */
@DisplayName("GrappleService — pull-session lifecycle and per-tick physics (WS8 G2+G3)")
class GrappleServiceTest {

	private static Grapple grapple(int maxDurationTicks) {
		return Grapple.builder()
		              .grappleId("test")
		              .maxDistance(25)
		              .maxPullSpeed(1.8)
		              .pullAcceleration(0.35)
		              .arrivalDistance(1.5)
		              .cooldownSeconds(8)
		              .maxDurationTicks(maxDurationTicks)
		              .fallDamageGraceTicks(40)
		              .requireLineOfSight(true)
		              .build();
	}

	private static Player onlinePlayer(World world, Location location) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(location);
		return player;
	}

	// Fix round 1 (F2): stubs World#isChunkLoaded(int,int) directly — GrappleService no longer calls
	// Location#getChunk() (which loads/generates the chunk it returns, so a mock built around it pinned an
	// unreachable production state).
	private static World loadedWorld() {
		World world = mock(World.class);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
		return world;
	}

	@Test
	@DisplayName("start() succeeds once; a second start() while active is rejected without replacing the session")
	void start_whileActive_rejectsAndKeepsOriginalSession() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));

		World  world     = loadedWorld();
		Player player    = onlinePlayer(world, new Location(world, 0, 64, 0));
		Grapple grapple  = grapple(100);
		Location anchor1 = new Location(world, 10, 64, 0);
		Location anchor2 = new Location(world, 20, 64, 0);

		assertTrue(service.start(player, grapple, anchor1));
		assertFalse(service.start(player, grapple, anchor2));
		assertSame(anchor1, service.getSession(player).getAnchor());
	}

	@Test
	@DisplayName("start() while on cooldown (after a cancel()) is rejected")
	void start_onCooldown_rejected() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));

		World   world  = loadedWorld();
		Player  player = onlinePlayer(world, new Location(world, 0, 64, 0));
		Grapple grapple = grapple(100);
		Location anchor = new Location(world, 10, 64, 0);

		assertTrue(service.start(player, grapple, anchor));
		service.cancel(player);

		assertFalse(service.start(player, grapple, anchor));
	}

	@Test
	@DisplayName("tickSession ends the pull once elapsedTicks reaches Max_Duration_Ticks")
	void tickSession_timeout_endsSession() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));

		World world = loadedWorld();
		// Anchor far enough away that arrival never triggers before the timeout does.
		Location anchor = new Location(world, 500, 64, 0);
		Player   player = onlinePlayer(world, new Location(world, 0, 64, 0));

		Grapple grapple = grapple(3);
		service.start(player, grapple, anchor);
		GrappleSession session = service.getSession(player);

		for (int i = 0; i < 3; i++) {
			service.tickSession(session);
		}

		assertFalse(service.isActive(player));
	}

	@Test
	@DisplayName("tickSession ends the pull when the anchor's chunk is unloaded (World#isChunkLoaded, F2)")
	void tickSession_chunkUnloaded_endsSession() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));

		World world = mock(World.class);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

		Location anchor = new Location(world, 500, 64, 0);
		Player   player = onlinePlayer(world, new Location(world, 0, 64, 0));

		Grapple grapple = grapple(100);
		service.start(player, grapple, anchor);
		GrappleSession session = service.getSession(player);

		service.tickSession(session);

		assertFalse(service.isActive(player));
	}

	@Test
	@DisplayName("tickSession caps velocity at Max_Pull_Speed and ramps up by roughly Pull_Acceleration per tick")
	void tickSession_velocity_rampsThenCaps() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));

		World world = loadedWorld();
		// Anchor far enough away (along +X) that arrival never triggers across the ticks below.
		Location anchor = new Location(world, 500, 64, 0);

		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.isOnline()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));

		Grapple grapple = grapple(100);
		service.start(player, grapple, anchor);
		GrappleSession session = service.getSession(player);

		ArgumentCaptor<Vector> captor = ArgumentCaptor.forClass(Vector.class);

		// pullAcceleration=0.35, maxPullSpeed=1.8 -> caps on the 6th tick (0.35*5=1.75 < 1.8 <= 0.35*6=2.1).
		for (int i = 0; i < 8; i++) {
			service.tickSession(session);
		}
		verify(player, org.mockito.Mockito.times(8)).setVelocity(captor.capture());

		double previousLength = 0.0;
		for (Vector velocity : captor.getAllValues()) {
			assertTrue(velocity.length() <= grapple.getMaxPullSpeed() + 1e-9);
			assertTrue(velocity.length() >= previousLength - 1e-9);
			previousLength = velocity.length();

			// Fix round 1 minor: pull direction, not just magnitude — the mocked player never actually moves
			// (getLocation() is a fixed stub), so every tick's velocity must point exactly along +X toward the
			// anchor at (500, 64, 0) from (0, 64, 0).
			Vector direction = velocity.clone().normalize();
			assertEquals(1.0, direction.getX(), 1e-9);
			assertEquals(0.0, direction.getY(), 1e-9);
			assertEquals(0.0, direction.getZ(), 1e-9);
		}
		assertEquals(grapple.getMaxPullSpeed(), previousLength, 1e-9);
	}

	@Test
	@DisplayName("tickSession auto-stops on arrival and grants the one-shot landing grace")
	void tickSession_arrival_stopsAndGrantsGrace() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));

		World world = loadedWorld();
		Location anchor = new Location(world, 10, 64, 0);
		// Within arrivalDistance (1.5) of the anchor.
		Player player = onlinePlayer(world, new Location(world, 10, 64, 1));

		Grapple grapple = grapple(100);
		service.start(player, grapple, anchor);
		GrappleSession session = service.getSession(player);

		service.tickSession(session);

		assertFalse(service.isActive(player));
		assertTrue(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("consumeLandingGrace is one-shot: true once, false on the immediate second call")
	void consumeLandingGrace_isOneShot() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));

		World   world   = loadedWorld();
		Player  player  = onlinePlayer(world, new Location(world, 0, 64, 0));
		Grapple grapple = grapple(100);
		Location anchor = new Location(world, 10, 64, 0);

		service.start(player, grapple, anchor);
		service.cancel(player);

		assertTrue(service.consumeLandingGrace(player));
		assertFalse(service.consumeLandingGrace(player));
	}

	@Test
	@DisplayName("forget() drops the active session, cooldown and landing grace immediately (F3, quit mid-pull)")
	void forget_quitMidPull_clearsEverySessionMap() {
		GrappleService service = new GrappleService(mock(JavaPlugin.class));

		World   world   = loadedWorld();
		Player  player  = onlinePlayer(world, new Location(world, 0, 64, 0));
		Grapple grapple = grapple(100);
		Location anchor = new Location(world, 10, 64, 0);

		assertTrue(service.start(player, grapple, anchor));
		assertTrue(service.isActive(player));

		service.forget(player);

		assertFalse(service.isActive(player), "the session must be gone the same tick as the quit");
		assertFalse(service.isOnCooldown(player), "forget() must also clear the cooldown");
		assertFalse(service.consumeLandingGrace(player), "forget() must NOT grant a landing grace — the player quit, they didn't land");

		// Re-launching immediately must now succeed — nothing left over from the forgotten session.
		assertTrue(service.start(player, grapple, anchor));
	}
}
