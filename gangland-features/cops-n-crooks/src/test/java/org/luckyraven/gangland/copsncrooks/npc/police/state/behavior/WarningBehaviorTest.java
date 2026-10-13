package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings.Result;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolFixture;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.20.0 lane patrol (P-PAT-2): the beat cop faces the warned player and holds until {@link BrandishWarnings} decides,
 * then goes back to PATROL. It never attacks; the crime itself is committed by the warning clock.
 */
@DisplayName("WarningBehavior - face the player, hold, hand back to PATROL")
class WarningBehaviorTest {

	private PatrolFixture    fx;
	private BrandishWarnings warnings;
	private CopPerception    perception;
	private CopNpc           cop;
	private Player           player;
	private WarningBehavior  behavior;

	@BeforeEach
	void setUp() {
		fx         = new PatrolFixture();
		warnings   = mock(BrandishWarnings.class);
		perception = new CopPerception();
		PatrolServices services = new PatrolServices(() -> PatrolSettings.DEFAULT, () -> WarningSettings.DEFAULT,
		                                             perception, p -> false, id -> null, warnings,
		                                             mock(CopRadio.class), () -> List.of(), e -> false,
		                                             () -> fx.clock[0], new Random(1));
		behavior = new WarningBehavior(services);
		cop      = fx.cop(CopState.WARNING, 0, 0, fx.at(0, 0));
		player   = fx.player(5, 0);
		cop.setTargetPlayerId(player.getUniqueId());
		when(warnings.update(any(), anyBoolean())).thenReturn(Result.PENDING);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	@Test
	@DisplayName("each tick the cop turns to face the warned player and the warning stays open")
	void facesThePlayer_andHolds() {
		behavior.tick(cop);

		verify(cop.getNpc()).faceLocation(player.getLocation());
		verify(warnings).update(player, true);
		assertEquals(CopState.WARNING, cop.getCurrentState());
		verify(cop, never()).navigateTo(any());
	}

	@Test
	@DisplayName("a player the cop no longer acquires is out of view for the warning clock")
	void notAcquired_outOfView() {
		perception.addLockOnFilter((c, p) -> false);

		behavior.tick(cop);

		verify(warnings).update(player, false);
	}

	@Test
	@DisplayName("a complied warning sends the cop back to PATROL and clears the target")
	void complied_backToPatrol() {
		when(warnings.update(player, true)).thenReturn(Result.COMPLIED);

		behavior.tick(cop);

		assertEquals(CopState.PATROL, cop.getCurrentState());
		assertEquals(null, cop.getTargetPlayerId());
	}

	@Test
	@DisplayName("an ignored warning (the clock committed the crime) sends the cop back to PATROL")
	void ignored_backToPatrol() {
		when(warnings.update(player, true)).thenReturn(Result.IGNORED);

		behavior.tick(cop);

		assertEquals(CopState.PATROL, cop.getCurrentState());
	}

	@Test
	@DisplayName("a warned player who left is abandoned without a verdict and the cop goes back to PATROL")
	void playerOffline_abandons() {
		java.util.UUID id = player.getUniqueId();
		fx.quit(player);

		behavior.tick(cop);

		verify(warnings).abandon(id);
		verify(warnings, never()).update(any(), anyBoolean());
		assertEquals(CopState.PATROL, cop.getCurrentState());
	}

	@Test
	@DisplayName("a warned player who died is abandoned and the cop goes back to PATROL")
	void playerDead_abandons() {
		when(player.isDead()).thenReturn(true);

		behavior.tick(cop);

		verify(warnings).abandon(player.getUniqueId());
		assertEquals(CopState.PATROL, cop.getCurrentState());
	}

	@Test
	@DisplayName("no target at all: straight back to PATROL, no warning update")
	void noTarget_backToPatrol() {
		cop.setTargetPlayerId(null);

		behavior.tick(cop);

		assertEquals(CopState.PATROL, cop.getCurrentState());
		verify(warnings, never()).update(any(), anyBoolean());
	}

	@Test
	@DisplayName("warning never attacks, even when the warning is ignored")
	void neverAttacks() {
		when(warnings.update(player, true)).thenReturn(Result.IGNORED);

		for (int i = 0; i < 20; i++) {
			cop.transitionTo(CopState.WARNING);
			behavior.tick(cop);
		}

		verify(cop, never()).attack(any(Player.class));
		verify(cop, never()).attackEntity(any(LivingEntity.class));
		verify(cop, never()).pursue(any(), any(), anyDouble());
	}

	@Test
	@DisplayName("onEnter stops the cop walking; onExit abandons any warning still open for the target")
	void lifecycle() {
		behavior.onEnter(cop);
		verify(cop).stopNavigation();

		behavior.onExit(cop);
		verify(warnings).abandon(player.getUniqueId());
	}

	@Test
	@DisplayName("the target cleared by CopNpc.tick before the behaviour runs still abandons the warned player")
	void warnedIdClearedByTick_stillAbandons() {
		java.util.UUID id = player.getUniqueId();
		behavior.onEnter(cop);
		cop.setTargetPlayerId(null);

		behavior.tick(cop);

		verify(warnings).abandon(id);
		assertEquals(CopState.PATROL, cop.getCurrentState());
	}

	@Test
	@DisplayName("a cop hit mid-warning (target swapped to the attacker) abandons the warned player on exit")
	void attackedDuringWarning_abandonsWarnedPlayer() {
		java.util.UUID id = player.getUniqueId();
		behavior.onEnter(cop);
		cop.setTargetPlayerId(java.util.UUID.randomUUID());

		behavior.onExit(cop);

		verify(warnings).abandon(id);
	}
}
