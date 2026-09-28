package org.luckyraven.gangland.civilians.npc.state.behavior;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.FactionSquads;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianDropConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianWearableConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcCoverStatus;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcMeleeProfile;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.10): a civilian in COMBAT moves through Keystone's squad pursuit with its type's Alert_Range, and
 * gives up (clears its target, IDLE) once nobody in its squad has seen the target for Search_Seconds - the old
 * {@code attackRange * 4} give-up distance is gone.
 */
@DisplayName("CivilianCombatBehavior - squad pursuit and the search window")
class CivilianCombatBehaviorTest {

	private static final double ALERT_RANGE    = 16.0;
	private static final int    SEARCH_SECONDS = 20;

	private World        world;
	private CivilianNpc  npc;
	private LivingEntity target;
	private UUID         targetId;

	@BeforeEach
	void setUp() {
		world    = mock(World.class);
		targetId = UUID.randomUUID();
		target   = mock(LivingEntity.class);
		when(target.getUniqueId()).thenReturn(targetId);
		when(target.getLocation()).thenReturn(new Location(world, 5, 64, 5));

		CivilianAIBehaviorConfig ai = new CivilianAIBehaviorConfig(false, 0, false, 0, true, 4.0, 12.0, 20,
		                                                           NpcDifficulty.NORMAL, ALERT_RANGE, SEARCH_SECONDS);
		npc = mock(CivilianNpc.class);
		when(npc.getTypeConfig()).thenReturn(new CivilianTypeConfig("gang_member", "Gang Member", EntityType.PLAYER,
		                                                            25.0, true, new CivilianWearableConfig("", "", "", ""),
		                                                            List.of(), List.of(), List.of(),
		                                                            new CivilianDropConfig(List.of(), 0.0), ai,
		                                                            "gang_member"));
		when(npc.getTargetEntity()).thenReturn(target);
	}

	@Test
	@DisplayName("nobody in the squad has seen the target for Search_Seconds: the civilian gives up")
	void searchWindowOver_givesUp() {
		NpcSquad squad = new NpcSquad(); // never sighted: millisSinceSighting() == Long.MAX_VALUE
		inSquad(squad);

		new CivilianCombatBehavior().tick(npc);

		verify(npc).pursue(target, squad, ALERT_RANGE);
		verify(npc).setTargetPlayerId(null);
		verify(npc).setTargetEntity(null);
		verify(npc).transitionTo(CivilianState.IDLE);
	}

	@Test
	@DisplayName("a fresh squad sighting keeps the civilian fighting")
	void freshSighting_keepsFighting() {
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);

		new CivilianCombatBehavior().tick(npc);

		verify(npc).pursue(target, squad, ALERT_RANGE);
		verify(npc, never()).transitionTo(any());
	}

	@Test
	@DisplayName("no squad yet, no FactionSquads wired: the civilian hunts alone from where the target is now")
	void noFactionSquads_fallsBackToPrivate() {
		when(npc.getFactionSquads()).thenReturn(null);

		new CivilianCombatBehavior().tick(npc);

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(npc).joinSquad(squad.capture(), eq(targetId));
		assertEquals(new Location(world, 5, 64, 5), squad.getValue().lastKnownLocation());
		verify(npc).pursue(target, squad.getValue(), ALERT_RANGE);
		verify(npc, never()).transitionTo(any());
	}

	@Test
	@DisplayName("no squad yet, FactionSquads wired: delegates to it instead of hunting alone")
	void nonHitEntry_usesFactionSquad() {
		FactionSquads factionSquads = mock(FactionSquads.class);
		NpcSquad       squad        = new NpcSquad();
		when(factionSquads.squadFor(npc, target)).thenReturn(squad);
		when(npc.getFactionSquads()).thenReturn(factionSquads);

		new CivilianCombatBehavior().tick(npc);

		verify(factionSquads).squadFor(npc, target);
		verify(npc, never()).joinSquad(any(), any());
		verify(npc).pursue(target, squad, ALERT_RANGE);
	}

	@Test
	@DisplayName("a ranged civilian at 13 blocks (beyond Attack_Range, inside Alert_Range) still attacks")
	void rangedCivilianAt13Blocks_stillAttacks() {
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);
		when(npc.isRangedAttacker()).thenReturn(true);
		when(npc.distanceTo(target)).thenReturn(13.0);
		when(npc.canAttack()).thenReturn(true);
		when(npc.hasLineOfSight(target)).thenReturn(true);

		new CivilianCombatBehavior().tick(npc);

		verify(npc).attackEntity(target);
	}

	@Test
	@DisplayName("a ranged civilian beyond Alert_Range does not attack")
	void rangedCivilianBeyondAlertRange_doesNotAttack() {
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);
		when(npc.isRangedAttacker()).thenReturn(true);
		when(npc.distanceTo(target)).thenReturn(ALERT_RANGE + 1);
		when(npc.canAttack()).thenReturn(true);
		when(npc.hasLineOfSight(target)).thenReturn(true);

		new CivilianCombatBehavior().tick(npc);

		verify(npc, never()).attackEntity(any());
		verify(npc, never()).attack(any());
	}

	@Test
	@DisplayName("a melee civilian at 13 blocks (beyond Attack_Range, inside Alert_Range) does not attack")
	void meleeCivilian_usesAttackRange() {
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);
		when(npc.isRangedAttacker()).thenReturn(false);
		when(npc.distanceTo(target)).thenReturn(13.0);
		when(npc.canAttack()).thenReturn(true);
		when(npc.hasLineOfSight(target)).thenReturn(true);

		new CivilianCombatBehavior().tick(npc);

		verify(npc, never()).attackEntity(any());
	}

	@Test
	@DisplayName("badly hurt: takes cover instead of pursuing or attacking")
	void badlyHurtCivilian_takesCoverInsteadOfPursuing() {
		LivingEntity self = mock(LivingEntity.class);
		when(self.getHealth()).thenReturn(5.0);
		when(self.getMaxHealth()).thenReturn(20.0);
		when(npc.getEntity()).thenReturn(self);
		when(npc.takeCover(target, 12.0)).thenReturn(NpcCoverStatus.MOVING);
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);
		when(npc.getCurrentSquad()).thenReturn(squad); // already hunting with it: Keystone radios to it

		new CivilianCombatBehavior().tick(npc);

		verify(npc).takeCover(target, 12.0);
		verify(npc, never()).pursue(any(), any(), anyDouble());
	}

	@Test
	@DisplayName("badly hurt but no cover within Radius (FAILED): fights on - pursues and attacks")
	void badlyHurt_noCover_fightsOn() {
		badlyHurt();
		when(npc.takeCover(target, 12.0)).thenReturn(NpcCoverStatus.FAILED);
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);
		when(npc.getCurrentSquad()).thenReturn(squad);
		when(npc.distanceTo(target)).thenReturn(2.0);
		when(npc.canAttack()).thenReturn(true);
		when(npc.hasLineOfSight(target)).thenReturn(true);

		new CivilianCombatBehavior().tick(npc);

		verify(npc).pursue(target, squad, ALERT_RANGE);
		verify(npc).attackEntity(target);
	}

	@Test
	@DisplayName("badly hurt and hiding: still gives up once nobody has seen the target for Search_Seconds")
	void badlyHurt_hiding_stillGivesUpAfterSearchWindow() {
		badlyHurt();
		when(npc.takeCover(target, 12.0)).thenReturn(NpcCoverStatus.ARRIVED);
		inSquad(new NpcSquad()); // never sighted

		new CivilianCombatBehavior().tick(npc);

		verify(npc).transitionTo(CivilianState.IDLE);
	}

	@Test
	@DisplayName("badly hurt on entering combat: still joins its faction squad, so its retreat is heard")
	void badlyHurt_onEntry_joinsFactionSquad() {
		badlyHurt();
		when(npc.takeCover(target, 12.0)).thenReturn(NpcCoverStatus.MOVING);
		FactionSquads factionSquads = mock(FactionSquads.class);
		NpcSquad       squad        = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		when(factionSquads.squadFor(npc, target)).thenReturn(squad);
		when(npc.getFactionSquads()).thenReturn(factionSquads);

		new CivilianCombatBehavior().tick(npc);

		verify(factionSquads).squadFor(npc, target);
		// Keystone radios Fall_Back/In_Cover to the squad of the last pursue (none yet this stint): hunt with the
		// faction squad first, so the retreat is heard
		InOrder order = inOrder(npc);
		order.verify(npc).pursue(target, squad, ALERT_RANGE);
		order.verify(npc).takeCover(target, 12.0);
	}

	@Test
	@DisplayName("badly hurt, no cover (FAILED): not asked again for 5 s, so it works its post instead of repathing")
	void badlyHurt_noCover_retriesOnlyAfterWindow() {
		badlyHurt();
		when(npc.takeCover(target, 12.0)).thenReturn(NpcCoverStatus.FAILED);
		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);
		when(npc.getCurrentSquad()).thenReturn(squad);
		long[]                 now      = {1_000};
		CivilianCombatBehavior behavior = new CivilianCombatBehavior(() -> now[0]);

		behavior.tick(npc);
		now[0] += 4_999;
		behavior.tick(npc);

		verify(npc, times(1)).takeCover(target, 12.0);
		verify(npc, times(2)).pursue(target, squad, ALERT_RANGE);

		now[0] += 1;
		behavior.tick(npc);

		verify(npc, times(2)).takeCover(target, 12.0);
	}

	private void badlyHurt() {
		LivingEntity self = mock(LivingEntity.class);
		when(self.getHealth()).thenReturn(5.0);
		when(self.getMaxHealth()).thenReturn(20.0);
		when(npc.getEntity()).thenReturn(self);
	}

	@Test
	@DisplayName("Combat.Retreat disabled: fights on even at low health")
	void retreatDisabled_pursuesNormallyEvenWhenBadlyHurt() {
		CivilianAIBehaviorConfig ai = new CivilianAIBehaviorConfig(false, 0, false, 0, true, 4.0, 12.0, 20,
		                                                           NpcDifficulty.NORMAL, ALERT_RANGE, SEARCH_SECONDS,
		                                                           CivilianAIBehaviorConfig.DEFAULT_TACTICS,
		                                                           NpcMeleeProfile.DEFAULT, 1.0,
		                                                           new RetreatSettings(false, 0.3, 12.0));
		when(npc.getTypeConfig()).thenReturn(new CivilianTypeConfig("gang_member", "Gang Member", EntityType.PLAYER,
		                                                            25.0, true, new CivilianWearableConfig("", "", "", ""),
		                                                            List.of(), List.of(), List.of(),
		                                                            new CivilianDropConfig(List.of(), 0.0), ai,
		                                                            "gang_member"));
		LivingEntity self = mock(LivingEntity.class);
		when(self.getHealth()).thenReturn(1.0);
		when(self.getMaxHealth()).thenReturn(20.0);
		when(npc.getEntity()).thenReturn(self);

		NpcSquad squad = new NpcSquad();
		squad.reportSighting(new Location(world, 5, 64, 5));
		inSquad(squad);

		new CivilianCombatBehavior().tick(npc);

		verify(npc, never()).takeCover(any(), anyDouble());
		verify(npc).pursue(target, squad, ALERT_RANGE);
	}

	@Test
	@DisplayName("leaving combat leaves the squad")
	void onExit_leavesSquad() {
		new CivilianCombatBehavior().onExit(npc);

		verify(npc).leaveSquad();
	}

	private void inSquad(NpcSquad squad) {
		when(npc.getSquad()).thenReturn(squad);
		when(npc.getSquadTargetId()).thenReturn(targetId);
	}
}
