package org.luckyraven.gangland.fights.fight;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how an {@link ArenaPoint} becomes a Bukkit {@code Location} (the snapshot put-back and the world's teleport)
 * and how a {@code Location} becomes an {@code ArenaPoint} (the snapshot), with six distinct components so a swapped
 * pair shows.
 */
class SnapshotPlacementTest {

	private static final ArenaPoint POINT = new ArenaPoint("w", 1.5, 2.5, 3.5, 4.5f, 5.5f);

	private MockedStatic<Bukkit> bukkit;
	private World                world;
	private Player               player;
	private UUID                 id;

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		world  = mock(World.class);
		when(world.getName()).thenReturn("w");
		bukkit.when(() -> Bukkit.getWorld("w")).thenReturn(world);

		id     = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
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

	private BukkitFightWorld fightWorld() {
		return new BukkitFightWorld(mock(GangManager.class), mock(MemberManager.class), mock(UserManager.class),
		                            mock(FightMessages.class));
	}

	// ----------------------------------------------------------- PlayerSnapshot.restore

	@Test
	@DisplayName("restore teleports to the saved point, then sets health and food")
	void restore_teleportsToTheSavedPointAndRestoresVitals() {
		when(player.getMaxHealth()).thenReturn(20D);

		new PlayerSnapshot(id, POINT, 12D, 9).restore(player);

		ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
		verify(player).teleport(to.capture());
		assertPlacedAt(to.getValue(), world);
		verify(player).setHealth(12D);
		verify(player).setFoodLevel(9);
	}

	@Test
	@DisplayName("restore keeps health within 1 and the maximum and still restores vitals when the world is gone")
	void restore_worldMissing_stillRestoresVitals() {
		when(player.getMaxHealth()).thenReturn(20D);

		new PlayerSnapshot(id, new ArenaPoint("gone", 1, 2, 3, 4f, 5f), 100D, 7).restore(player);

		verify(player, never()).teleport(any(Location.class));
		verify(player).setHealth(20D);
		verify(player).setFoodLevel(7);
	}

	// ----------------------------------------------------------- BukkitFightWorld.teleport

	@Test
	@DisplayName("the world's teleport sends an online player to the point inside an expected-teleport window")
	void teleport_sendsTheOnlinePlayerToThePoint() {
		boolean[] expectedDuring = new boolean[1];
		when(player.teleport(any(Location.class))).thenAnswer(call -> {
			expectedDuring[0] = org.luckyraven.gangland.fights.listener.ExpectedTeleports.expected(id);
			return true;
		});

		assertTrue(fightWorld().teleport(id, POINT));

		ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
		verify(player).teleport(to.capture());
		assertPlacedAt(to.getValue(), world);
		assertTrue(expectedDuring[0]);
		assertFalse(org.luckyraven.gangland.fights.listener.ExpectedTeleports.expected(id));
	}

	@Test
	@DisplayName("the world's teleport refuses an offline player or an unloaded world without teleporting")
	void teleport_offlineOrUnloadedWorld_refuses() {
		assertFalse(fightWorld().teleport(UUID.randomUUID(), POINT));
		assertFalse(fightWorld().teleport(id, new ArenaPoint("gone", 1, 2, 3, 4f, 5f)));

		verify(player, never()).teleport(any(Location.class));
	}

	// ----------------------------------------------------------- BukkitFightWorld.snapshot

	@Test
	@DisplayName("a snapshot records the player's position as an ArenaPoint with the world name, plus health and food")
	void snapshot_recordsPositionHealthAndFood() {
		when(player.getLocation()).thenReturn(new Location(world, 1.5, 2.5, 3.5, 4.5f, 5.5f));
		when(player.getHealth()).thenReturn(17D);
		when(player.getFoodLevel()).thenReturn(11);

		assertEquals(new PlayerSnapshot(id, POINT, 17D, 11), fightWorld().snapshot(id));
	}

	@Test
	@DisplayName("a snapshot taken in no world records an empty world name")
	void snapshot_noWorld_recordsAnEmptyWorldName() {
		when(player.getLocation()).thenReturn(new Location(null, 1.5, 2.5, 3.5, 4.5f, 5.5f));
		when(player.getHealth()).thenReturn(17D);
		when(player.getFoodLevel()).thenReturn(11);

		assertEquals(new PlayerSnapshot(id, new ArenaPoint("", 1.5, 2.5, 3.5, 4.5f, 5.5f), 17D, 11),
		             fightWorld().snapshot(id));
	}
}
