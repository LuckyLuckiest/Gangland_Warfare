package org.luckyraven.gangland.copsncrooks.npc.police;

import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9): the cops' squad learns where the wanted player is from the crime scene at wanted start, and
 * again whenever he hits one of the group's cops - never from an unrelated attacker. Phase H12 (GL-3): every group
 * speaks on the police radio, spawns at its tier's formation arc, announces escalations, takes (and gives back)
 * backup, and turns on a suspect who resists.
 */
@DisplayName("CopManager - squads, radio, backup and escalation")
class CopManagerSquadTest {

	private static final CopRole MEDIC = new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, 8.0, 12.0, 1.0, null,
	                                                 0, null, 1.0, 0, null, 0, 60, true, false);

	private CopManagerFixture fx;
	private CopManager        manager;
	private Player            player;
	private UUID              playerId;
	private Wanted            wanted;

	@BeforeEach
	void setUp() {
		fx       = new CopManagerFixture();
		manager  = fx.manager;
		player   = fx.player(10, 10);
		playerId = player.getUniqueId();
		wanted   = CopManagerFixture.wanted(2);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	@Test
	@DisplayName("a wanted start reports the crime scene to the new group's squad")
	void wantedStart_reportsCrimeScene() {
		manager.onWantedStart(player, wanted);

		NpcSquad squad = manager.groupFor(playerId).getSquad();
		assertTrue(squad.hasFreshSighting());
		assertEquals(new Location(fx.world, 10, 64, 10), squad.lastKnownLocation());
	}

	@Test
	@DisplayName("the group's target hitting a cop gives his position away; an unrelated attacker does not")
	void copAttacked_reportsOnlyTheGroupTarget() {
		manager.onWantedStart(player, wanted);
		CopNpc cop = mock(CopNpc.class);
		manager.groupFor(playerId).add(cop);

		Player stranger = fx.player(90, 90);
		manager.onCopAttackedAlert(cop, stranger);

		assertEquals(new Location(fx.world, 10, 64, 10), manager.groupFor(playerId).getSquad().lastKnownLocation());

		when(player.getLocation()).thenReturn(new Location(fx.world, 40, 70, 40));
		manager.onCopAttackedAlert(cop, player);

		assertEquals(new Location(fx.world, 40, 70, 40), manager.groupFor(playerId).getSquad().lastKnownLocation());
	}

	@Test
	@DisplayName("a new wanted start sends the group's returning cops back to pursuit; other states are untouched")
	void wantedStart_sendsReturningCopsBackToPursuit() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);

		CopNpc returningCop = fx.cop(CopState.RETURNING, 0, 0);
		group.add(returningCop);
		CopNpc pursuingCop = fx.cop(CopState.PURSUING, 0, 0);
		group.add(pursuingCop);

		manager.onWantedStart(player, wanted);

		assertEquals(CopState.PURSUING, returningCop.getCurrentState());
		assertEquals(playerId, returningCop.getTargetPlayerId());
		verify(pursuingCop, never()).setTargetPlayerId(any());
		verify(pursuingCop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a new group's squad speaks on the police radio")
	void newGroup_squadHasRadioListener() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = mock(CopNpc.class);
		group.add(cop);

		group.getSquad().memberDown(cop);

		assertNotNull(fx.listeners.get(group));
		verify(fx.listeners.get(group)).onSignal(eq(group.getSquad()), any(NpcSquadSignal.class), eq(cop), any());
	}

	@Test
	@DisplayName("dispatch announces a new hunt once; a repeated start with a fresh trail stays quiet")
	void wantedStart_dispatchesOnceForANewHunt() {
		manager.onWantedStart(player, wanted);
		manager.onWantedStart(player, wanted);

		verify(fx.radio, times(1)).dispatch(manager.groupFor(playerId), player, "Dispatch_Wanted", 2, "SWAT");
	}

	@Test
	@DisplayName("the spawn task gives the group squad its tier's formation arc and fills the wanted-level count")
	void spawnTask_setsTierFormationArc() {
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);

		CopGroup group = manager.groupFor(playerId);
		assertEquals(270.0, group.getSquad().getFormationArc());
		assertEquals(2, group.getCops().size());
		assertEquals("SWAT", group.getTierName());
	}

	@Test
	@DisplayName("RETURNING cops beyond the pursuit range do not fill the spawn count; nearby ones do")
	void spawnTask_ignoresStrandedReturningCops() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.RETURNING, 500, 500));
		group.add(fx.cop(CopState.RETURNING, 500, -500));

		manager.spawnTick(playerId, wanted);

		assertEquals(4, group.getCops().size(), "two fresh cops despite two stranded returners");

		CopGroup near = manager.groupFor(playerId);
		near.getCops().clear();
		near.add(fx.cop(CopState.RETURNING, 10, 10));
		near.add(fx.cop(CopState.RETURNING, 12, 10));
		manager.spawnTick(playerId, wanted);
		assertEquals(2, near.getCops().size(), "nearby returners still count");
	}

	@Test
	@DisplayName("each new cop gets the composition's next role; a stranded RETURNING cop does not hold its role")
	void spawnTask_fillsCompositionRoles_strandedRoleRefilled() {
		CopRole pointman = role("Pointman");
		CopRole assault  = role("Assault");
		when(fx.provider.getSquadComposition(2)).thenReturn(List.of(pointman, assault));
		manager.onWantedStart(player, wanted);
		CopGroup group    = manager.groupFor(playerId);
		CopNpc   stranded = fx.cop(CopState.RETURNING, 500, 500);
		when(stranded.getRole()).thenReturn(pointman);
		group.add(stranded);

		manager.spawnTick(playerId, wanted);

		verify(fx.spawner).spawnNearPlayer(player, 3, pointman);
		verify(fx.spawner).spawnNearPlayer(player, 3, assault);
	}

	@Test
	@DisplayName("with roles off (no composition) cops spawn with no role")
	void spawnTask_noComposition_noRole() {
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);

		verify(fx.spawner, times(2)).spawnNearPlayer(player, 3, null);
	}

	@Test
	@DisplayName("a tier rise is announced once by dispatch; the first spawn and a steady tier are not")
	void tierRise_dispatchesEscalateOnce() {
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);
		fx.tier[0] = 4;
		manager.spawnTick(playerId, wanted);
		manager.spawnTick(playerId, wanted);

		verify(fx.radio, times(1)).dispatch(any(), eq(player), eq("Escalate"), anyInt(), anyString());
	}

	@Test
	@DisplayName("granted backup adds its extra cop, still capped by Max_Per_Player")
	void backupActive_addsExtraCop_cappedByMaxPerPlayer() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		manager.spawnTick(playerId, wanted);
		assertEquals(2, group.getCops().size());

		assertTrue(group.requestBackup(fx.clock[0], fx.provider.getBackupSettings()));
		manager.spawnTick(playerId, wanted);
		assertEquals(3, group.getCops().size());

		when(fx.provider.getMaxCopsPerPlayer()).thenReturn(3);
		when(fx.provider.getBackupSettings()).thenReturn(new BackupSettings(true, 5, 30_000, 60_000));
		manager.spawnTick(playerId, wanted);
		assertEquals(3, group.getCops().size());
	}

	@Test
	@DisplayName("when backup runs out the newest free cop walks home; fighting or cuffing cops never do, and it retries")
	void backupExpired_surplusCopReturns_onlyWhenNotEngaged() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.requestBackup(fx.clock[0], fx.provider.getBackupSettings());
		manager.spawnTick(playerId, wanted);
		List<CopNpc> cops = List.copyOf(group.getCops());
		assertEquals(3, cops.size());

		cops.get(0).transitionTo(CopState.COMBAT);
		cops.get(1).transitionTo(CopState.CUFFING);
		cops.get(2).transitionTo(CopState.COMBAT);
		fx.clock[0] += 31_000;
		manager.spawnTick(playerId, wanted);

		assertEquals(1, group.getPendingRelease(), "no free cop yet: the release waits");
		for (CopNpc cop : cops) assertFalse(cop.getCurrentState() == CopState.RETURNING);

		cops.get(1).transitionTo(CopState.PURSUING);
		cops.get(2).transitionTo(CopState.PURSUING);
		fx.clock[0] += 1_000;
		manager.spawnTick(playerId, wanted);

		assertEquals(CopState.RETURNING, cops.get(2).getCurrentState(), "newest free cop goes first");
		assertEquals(CopState.PURSUING, cops.get(1).getCurrentState());
		assertEquals(CopState.COMBAT, cops.get(0).getCurrentState());
		assertEquals(0, group.getPendingRelease());
		assertEquals(3, group.getCops().size(), "it despawns on its own, not replaced meanwhile");
	}

	@Test
	@DisplayName("a resisting suspect: Resisting is said once, the group's cops fight, and new spawns come combat-forced")
	void resistingEscalation_newSpawnsCombatForced_resistingSaidOnce() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		cop.setTargetPlayerId(playerId);
		group.add(cop);

		group.escalate(playerId);
		manager.aiTick(playerId);
		manager.aiTick(playerId);

		verify(fx.radio, times(1)).sayFromLeader(group, "Resisting");
		assertEquals(CopState.COMBAT, cop.getCurrentState());
		assertTrue(cop.isCombatForced());

		manager.spawnTick(playerId, wanted);
		CopNpc spawned = group.getCops().get(1);
		assertTrue(spawned.isCombatForced());
	}

	@Test
	@DisplayName("each AI tick runs field care on the group: a cop at half health limps and radios Hit")
	void aiTick_runsFieldCare() {
		manager.onWantedStart(player, wanted);
		CopGroup     group = manager.groupFor(playerId);
		CopNpc       cop   = fx.cop(CopState.PURSUING, 0, 0);
		org.bukkit.entity.LivingEntity body = cop.getEntity();
		when(body.getHealth()).thenReturn(10.0);
		when(body.getMaxHealth()).thenReturn(20.0);
		group.add(cop);

		manager.aiTick(playerId);

		verify(cop).applySpeed(0.7);
		verify(fx.radio).sayAs(group, cop, "Hit", java.util.Map.of());
	}

	@Test
	@DisplayName("the wanted player hitting one of his pursuers is resisting too")
	void groupTargetHitsCop_escalates() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		group.add(cop);

		manager.onCopAttackedAlert(cop, player);

		assertTrue(group.isCombatAlert());
		assertTrue(group.pollResisting());
	}

	@Test
	@DisplayName("wanted end: the leader stands the group down and the resisting alert is forgotten")
	void wantedEnd_saysStandDown() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));
		group.escalate(playerId);

		manager.onWantedEnd(player);

		verify(fx.radio).sayFromLeader(group, "Stand_Down");
		assertFalse(group.isCombatAlert());
	}

	@Test
	@DisplayName("one wanted clear (WantedEndEvent then the level change to 0) announces Stand_Down once")
	void wantedEnd_calledTwice_saysStandDownOnce() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));
		group.escalate(playerId);

		manager.onWantedEnd(player);
		manager.onWantedEnd(player);

		verify(fx.radio, times(1)).sayFromLeader(group, "Stand_Down");
	}

	@Test
	@DisplayName("shutdown ends field care before the cops are destroyed: the medic is freed, the patient stands up")
	void shutdown_endsFieldCare_beforeDestroy() {
		manager.onWantedStart(player, wanted);
		CopGroup group   = manager.groupFor(playerId);
		CopNpc   patient = careCop(group, 10.0, null);
		CopNpc   medic   = careCop(group, 20.0, MEDIC);
		manager.aiTick(playerId);
		assertSame(patient, medic.getPatient());
		assertTrue(patient.isUnderCare());

		manager.shutdown();

		InOrder medicOrder   = inOrder(medic);
		InOrder patientOrder = inOrder(patient);
		medicOrder.verify(medic).setPatient(null);
		medicOrder.verify(medic).destroy(any());
		patientOrder.verify(patient).setUnderCare(false);
		patientOrder.verify(patient).destroy(any());
	}

	@Test
	@DisplayName("a group the AI tick drops (empty, player no longer wanted) ends its field care: the medic is freed, the patient stands up")
	void emptyGroupDropped_endsFieldCare() {
		manager.onWantedStart(player, wanted);
		CopGroup group   = manager.groupFor(playerId);
		CopNpc   patient = careCop(group, 10.0, null);
		CopNpc   medic   = careCop(group, 20.0, MEDIC);
		manager.aiTick(playerId);
		assertSame(patient, medic.getPatient());

		group.getCops().clear();
		when(fx.targeting.isWanted(playerId)).thenReturn(false);
		manager.aiTick(playerId);

		assertNull(manager.groupFor(playerId));
		assertNull(medic.getPatient());
		assertFalse(patient.isUnderCare());
	}

	/** A cop of {@code group} chasing the player, next to the others, at {@code health} of 20, with stateful care slots. */
	private CopNpc careCop(CopGroup group, double health, CopRole role) {
		CopNpc       cop  = fx.cop(CopState.PURSUING, 0, 0);
		cop.setTargetPlayerId(playerId);
		LivingEntity body = cop.getEntity();
		when(body.getHealth()).thenReturn(health);
		when(body.getMaxHealth()).thenReturn(20.0);
		when(cop.getRole()).thenReturn(role);
		when(cop.distanceTo(any(LivingEntity.class))).thenReturn(1.0);
		AtomicReference<CopNpc> patient = new AtomicReference<>();
		doAnswer(i -> {
			patient.set(i.getArgument(0));
			return null;
		}).when(cop).setPatient(any());
		when(cop.getPatient()).thenAnswer(i -> patient.get());
		AtomicBoolean underCare = new AtomicBoolean();
		doAnswer(i -> {
			underCare.set(i.getArgument(0));
			return null;
		}).when(cop).setUnderCare(anyBoolean());
		when(cop.isUnderCare()).thenAnswer(i -> underCare.get());
		group.add(cop);
		return cop;
	}

	private static CopRole role(String name) {
		return new CopRole(name, name, NpcFanPlacement.ANY, null, null, 1.0, null, 0, null, 1.0, 0, null, 0, 60, false,
		                   false);
	}
}
