package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code CopManager.inView} moved here unchanged: in front of the eyes (60 degrees), within range, with a clear line. */
@DisplayName("CopSightlines - the in-view cone")
class CopSightlinesTest {

	private World        world;
	private Player       player;
	private LivingEntity body;

	@BeforeEach
	void setUp() {
		world  = mock(World.class);
		player = mock(Player.class);
		body   = mock(LivingEntity.class);
		// yaw 0 looks along +Z, level
		when(player.getEyeLocation()).thenReturn(new Location(world, 0, 65.62, 0, 0f, 0f));
		when(player.hasLineOfSight(any(LivingEntity.class))).thenReturn(true);
	}

	private void bodyAt(double x, double y, double z) {
		when(body.getLocation()).thenReturn(new Location(world, x, y, z));
	}

	@Test
	@DisplayName("a body straight ahead inside the range with a clear line is in view")
	void aheadInRangeClear_inView() {
		bodyAt(0, 64, 10);

		assertTrue(CopSightlines.inView(player, body, 24));
	}

	@Test
	@DisplayName("a body behind the player is not in view")
	void behind_notInView() {
		bodyAt(0, 64, -10);

		assertFalse(CopSightlines.inView(player, body, 24));
	}

	@Test
	@DisplayName("a body beyond the range is not in view")
	void beyondRange_notInView() {
		bodyAt(0, 64, 30);

		assertFalse(CopSightlines.inView(player, body, 24));
	}

	@Test
	@DisplayName("a body in the cone behind a wall is not in view")
	void blockedLine_notInView() {
		bodyAt(0, 64, 10);
		when(player.hasLineOfSight(any(LivingEntity.class))).thenReturn(false);

		assertFalse(CopSightlines.inView(player, body, 24));
	}

	@Test
	@DisplayName("a body far above his head is outside the 60 degree cone")
	void overhead_notInView() {
		bodyAt(0, 80, 3);

		assertFalse(CopSightlines.inView(player, body, 24));
	}
}
