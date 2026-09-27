package org.luckyraven.gangland.copsncrooks.hud;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.12 F3: the escape compass. {@link EscapeCompass#pointOutside(Location, double, Location)} is pure math (the
 * point {@code radius + 5} blocks out along the ray from the zone center through the player); the instance keeps
 * each player's original compass target so it can be restored once the chase leaves SEARCHING.
 */
@DisplayName("EscapeCompass")
class EscapeCompassTest {

	private World world;

	@BeforeEach
	void setUp() {
		world = mock(World.class);
	}

	@Test
	@DisplayName("pointOutside extends the ray from center through the player by radius + 5")
	void pointOutside_extendsRay() {
		Location center = new Location(world, 0, 64, 0);
		Location player = new Location(world, 10, 70, 0);

		Location result = EscapeCompass.pointOutside(center, 40D, player);

		assertEquals(45D, result.getX(), 1.0E-9);
		assertEquals(0D, result.getZ(), 1.0E-9);
		assertEquals(64D, result.getY(), 1.0E-9, "y stays the zone center's");
	}

	@Test
	@DisplayName("pointOutside handles a diagonal direction, scaling both axes")
	void pointOutside_diagonal() {
		Location center = new Location(world, 0, 64, 0);
		Location player = new Location(world, 3, 70, 4); // 3-4-5 triangle, length 5

		Location result = EscapeCompass.pointOutside(center, 15D, player);

		// target distance = 15 + 5 = 20, scale = 20 / 5 = 4
		assertEquals(12D, result.getX(), 1.0E-9);
		assertEquals(16D, result.getZ(), 1.0E-9);
	}

	@Test
	@DisplayName("a player standing on the zone center is pointed east (+X)")
	void pointOutside_playerAtCenter_pointsEast() {
		Location center = new Location(world, 5, 64, 5);
		Location player = new Location(world, 5, 64, 5);

		Location result = EscapeCompass.pointOutside(center, 40D, player);

		assertEquals(50D, result.getX(), 1.0E-9);
		assertEquals(5D, result.getZ(), 1.0E-9);
	}

	@Test
	@DisplayName("point remembers the original target once, restore puts it back")
	void point_remembersOriginal_restorePutsItBack() {
		EscapeCompass compass = new EscapeCompass();
		Player        player  = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		Location original = new Location(world, 1, 2, 3);
		Location zoneEdge1 = new Location(world, 10, 64, 0);
		Location zoneEdge2 = new Location(world, 20, 64, 0);

		when(player.getCompassTarget()).thenReturn(original);

		compass.point(player, zoneEdge1);
		compass.point(player, zoneEdge2); // second call must not overwrite the remembered original

		verify(player).setCompassTarget(zoneEdge1);
		verify(player).setCompassTarget(zoneEdge2);

		compass.restore(player);

		verify(player).setCompassTarget(original);
	}

	@Test
	@DisplayName("restore is a no-op for a player never pointed")
	void restore_neverPointed_isNoOp() {
		EscapeCompass compass = new EscapeCompass();
		Player        player  = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		compass.restore(player);

		verify(player, never()).setCompassTarget(any());
	}
}
