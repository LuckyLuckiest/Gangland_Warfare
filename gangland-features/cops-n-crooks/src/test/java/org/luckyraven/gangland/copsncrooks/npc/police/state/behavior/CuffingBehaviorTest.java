package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CJ-23: a suspect who keeps breaking out of the cuffs is resisting. After {@code Max_Cuff_Attempts} failed or broken
 * cuffs the cop's group escalates and the cop fights; a lock another cop holds is not a failure.
 */
@DisplayName("CuffingBehavior - cuff escalation")
class CuffingBehaviorTest {

	private static final double CUFF_RADIUS  = 3.0;
	private static final int    MAX_ATTEMPTS = 3;

	private BukkitStatics    bukkit;
	private CopNpc           cop;
	private Player           player;
	private UUID             playerId;
	private CuffLockRegistry locks;
	private CopGroup         group;
	private CuffingBehavior  behavior;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install();
		playerId = UUID.randomUUID();
		player   = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.isOnline()).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		cop = mock(CopNpc.class);
		NPC npc = mock(NPC.class);
		when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
		when(cop.getNpc()).thenReturn(npc);
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		group = new CopGroup(playerId);
		when(cop.getGroup()).thenReturn(group);

		locks    = new CuffLockRegistry();
		behavior = new CuffingBehavior(CUFF_RADIUS, MAX_ATTEMPTS, 2, 5, locks, mock(DetainmentService.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("three break-outs: the group escalates (Resisting pending) and the cop fights")
	void threeEscapes_escalatesGroupAndEntersCombat() {
		escape();
		escape();
		verify(cop, times(2)).transitionTo(CopState.PURSUING);
		verify(cop, never()).transitionTo(CopState.COMBAT);

		escape();

		verify(cop).setCombatForced(true);
		verify(cop).transitionTo(CopState.COMBAT);
		assertTrue(group.isCombatAlert());
		assertTrue(group.pollResisting());
	}

	@Test
	@DisplayName("a cuff lock held by another cop is not a failed attempt")
	void lockDeniedIsNotAFailure() {
		locks.tryAcquire(playerId, UUID.randomUUID());
		for (int i = 0; i < MAX_ATTEMPTS + 1; i++) behavior.tick(cop);

		verify(cop, never()).transitionTo(CopState.COMBAT);
		assertFalse(group.isCombatAlert());
	}

	@Test
	@DisplayName("a successful cuff resets the count")
	void successResetsCount() {
		escape();
		escape();

		// in range through the whole wind-up, then the cuff lands
		when(cop.distanceTo(player)).thenReturn(1.0);
		when(cop.hasLineOfSight(player)).thenReturn(true);
		when(cop.attemptCuff(player)).thenReturn(true);
		behavior.onEnter(cop);
		for (int i = 0; i < 3; i++) behavior.tick(cop);
		verify(cop).transitionTo(CopState.GUARDING);
		behavior.onExit(cop);

		escape();
		escape();

		verify(cop, never()).transitionTo(CopState.COMBAT);
	}

	@Test
	@DisplayName("a new target resets the count")
	void newTargetResetsCount() {
		escape();
		escape();

		UUID   otherId = UUID.randomUUID();
		Player other   = mock(Player.class);
		when(other.getUniqueId()).thenReturn(otherId);
		when(other.isOnline()).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(otherId)).thenReturn(other);
		when(cop.getTargetPlayerId()).thenReturn(otherId);
		when(cop.distanceTo(other)).thenReturn(CUFF_RADIUS + 1);

		behavior.onEnter(cop);
		behavior.tick(cop);
		behavior.onExit(cop);

		verify(cop, never()).transitionTo(CopState.COMBAT);
	}

	@Test
	@DisplayName("three break-outs from three different officers: the group escalates on the third")
	void threeEscapesAcrossDifferentOfficers_escalatesOnce() {
		CopNpc          second  = officer();
		CopNpc          third   = officer();
		CuffingBehavior secondCuffing = new CuffingBehavior(CUFF_RADIUS, MAX_ATTEMPTS, 2, 5, locks,
		                                              mock(DetainmentService.class));
		CuffingBehavior thirdCuffing  = new CuffingBehavior(CUFF_RADIUS, MAX_ATTEMPTS, 2, 5, locks,
		                                              mock(DetainmentService.class));

		escape();
		escape(second, secondCuffing);
		verify(cop, never()).transitionTo(CopState.COMBAT);
		verify(second, never()).transitionTo(CopState.COMBAT);
		assertFalse(group.isCombatAlert());

		escape(third, thirdCuffing);

		verify(third).transitionTo(CopState.COMBAT);
		assertTrue(group.isCombatAlert());
		assertTrue(group.pollResisting());
		assertFalse(group.pollResisting());
	}

	private CopNpc officer() {
		CopNpc officer = mock(CopNpc.class);
		NPC    npc     = mock(NPC.class);
		when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
		when(officer.getNpc()).thenReturn(npc);
		when(officer.getTargetPlayerId()).thenReturn(playerId);
		when(officer.getGroup()).thenReturn(group);
		when(officer.distanceTo(player)).thenReturn(CUFF_RADIUS + 1);
		return officer;
	}

	private static void escape(CopNpc officer, CuffingBehavior cuffing) {
		cuffing.onEnter(officer);
		cuffing.tick(officer);
		cuffing.onExit(officer);
	}

	/** The cop claims the lock, and the suspect is already out of reach: one broken cuff. */
	private void escape() {
		when(cop.distanceTo(player)).thenReturn(CUFF_RADIUS + 1);
		behavior.onEnter(cop);
		behavior.tick(cop);
		behavior.onExit(cop);
	}
}
