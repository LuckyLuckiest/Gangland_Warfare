package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11, D1: a cop rotated out of a pursuit used to re-engage on its first RETURNING tick because its target was
 * still online and free, bouncing PURSUING <-> RETURNING forever. Only a cop that started returning because its
 * target was restrained or jailed may re-engage, when that target is released (the admin-release case).
 */
@DisplayName("ReturningBehavior - re-engage only after a restrained target is released")
class ReturningBehaviorTest {

	private BukkitStatics     bukkit;
	private DetainmentService detainmentService;
	private CopNpc            cop;
	private Player            player;
	private ReturningBehavior behavior;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();

		UUID playerId = UUID.randomUUID();
		player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		cop = mock(CopNpc.class);
		when(cop.getTargetPlayerId()).thenReturn(playerId);

		detainmentService = mock(DetainmentService.class);
		behavior          = new ReturningBehavior(mock(CopSpawnManager.class), detainmentService, 600, 3.0);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a cop rotated out while its target is free keeps returning instead of bouncing back (D1)")
	void rotatedOut_staysReturning() {
		behavior.onEnter(cop);
		behavior.tick(cop);
		behavior.tick(cop);

		verify(cop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a cop sent back by a restrained target re-engages once the target is released")
	void restrainedTargetReleased_reengages() {
		when(detainmentService.isRestrained(player)).thenReturn(true);
		behavior.onEnter(cop);
		when(detainmentService.isRestrained(player)).thenReturn(false);

		behavior.tick(cop);

		verify(cop).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("returning leaves the squad, so the cop no longer holds a slot or a route plan")
	void onEnter_leavesSquad() {
		behavior.onEnter(cop);

		verify(cop).leaveSquad();
	}
}
