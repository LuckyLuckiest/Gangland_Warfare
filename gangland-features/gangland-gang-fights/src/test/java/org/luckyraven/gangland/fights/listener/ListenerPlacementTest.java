package org.luckyraven.gangland.fights.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how the listeners turn an {@link ArenaPoint} into a Bukkit {@code Location}: {@code Fighters.teleport} (the
 * containment and join guards) and {@code FightSessionListener}'s respawn-exit helper. Six distinct components, so a
 * swapped pair shows.
 */
class ListenerPlacementTest {

	private static final ArenaPoint POINT = new ArenaPoint("w", 1.5, 2.5, 3.5, 4.5f, 5.5f);

	private MockedStatic<Bukkit> bukkit;
	private World                world;

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		world  = mock(World.class);
		bukkit.when(() -> Bukkit.getWorld("w")).thenReturn(world);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private static void assertPlacedAt(Location at, World expectedWorld) {
		assertSame(expectedWorld, at.getWorld());
		assertEquals(1.5, at.getX());
		assertEquals(2.5, at.getY());
		assertEquals(3.5, at.getZ());
		assertEquals(4.5f, at.getYaw());
		assertEquals(5.5f, at.getPitch());
	}

	@Test
	@DisplayName("Fighters.teleport sends the player to the point inside an expected-teleport window and returns the result")
	void fightersTeleport_sendsThePlayerToThePoint() {
		UUID   id     = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		boolean[] expectedDuring = new boolean[1];
		when(player.teleport(any(Location.class))).thenAnswer(call -> {
			expectedDuring[0] = ExpectedTeleports.expected(id);
			return true;
		});

		assertTrue(Fighters.teleport(player, POINT));

		ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
		verify(player).teleport(to.capture());
		assertPlacedAt(to.getValue(), world);
		assertTrue(expectedDuring[0]);
		assertFalse(ExpectedTeleports.expected(id));
	}

	@Test
	@DisplayName("Fighters.teleport answers false without teleporting when the world is not loaded")
	void fightersTeleport_worldMissing_returnsFalse() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		assertFalse(Fighters.teleport(player, new ArenaPoint("gone", 1, 2, 3, 4f, 5f)));

		verify(player, never()).teleport(any(Location.class));
	}

	private static Location respawnExit(ArenaPoint point) throws ReflectiveOperationException {
		Method location = FightSessionListener.class.getDeclaredMethod("location", ArenaPoint.class);
		location.setAccessible(true);
		return (Location) location.invoke(null, point);
	}

	@Test
	@DisplayName("the respawn exit helper builds the Location of the point, or null when the world is not loaded")
	void respawnExit_buildsTheLocationOrNull() throws ReflectiveOperationException {
		assertPlacedAt(respawnExit(POINT), world);
		assertNull(respawnExit(new ArenaPoint("gone", 1, 2, 3, 4f, 5f)));
	}
}
