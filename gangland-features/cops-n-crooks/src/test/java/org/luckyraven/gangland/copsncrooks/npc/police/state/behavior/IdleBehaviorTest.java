package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9): {@code Cops.Behaviour.Alert_Range} is the sight range. An idle cop that sees its target reports
 * the sighting to its squad, and any fresh squad sighting starts its pursuit.
 */
@DisplayName("IdleBehavior - squad perception")
class IdleBehaviorTest {

	private static final double ALERT_RANGE = 40.0;

	private BukkitStatics bukkit;
	private World         world;
	private CopNpc        cop;
	private Player        player;
	private NpcSquad      squad;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		world  = mock(World.class);

		UUID playerId = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getLocation()).thenReturn(new Location(world, 10, 64, 10));
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		squad = new NpcSquad();
		cop   = mock(CopNpc.class);
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		when(cop.squadFor(player)).thenReturn(squad);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("seeing the target within Alert_Range reports a sighting and starts the pursuit")
	void seesTarget_reportsSightingAndPursues() {
		when(cop.canSee(player, ALERT_RANGE)).thenReturn(true);

		new IdleBehavior(ALERT_RANGE).tick(cop);

		assertEquals(new Location(world, 10, 64, 10), squad.lastKnownLocation());
		verify(cop).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("a fresh squad sighting starts the pursuit of a cop that cannot see the target itself")
	void freshSquadSighting_startsPursuit() {
		squad.reportSighting(new Location(world, 0, 64, 0));

		new IdleBehavior(ALERT_RANGE).tick(cop);

		verify(cop).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("no sight and no fresh squad sighting: the cop stays idle")
	void nothingSeen_staysIdle() {
		new IdleBehavior(ALERT_RANGE).tick(cop);

		verify(cop, never()).transitionTo(any());
		assertNull(squad.lastKnownLocation());
	}
}
