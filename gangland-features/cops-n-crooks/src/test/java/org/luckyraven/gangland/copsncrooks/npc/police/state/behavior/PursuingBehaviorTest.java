package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcCoverStatus;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9, D1): a pursuing cop no longer returns because pathfinding gave up - it navigates through
 * Keystone's squad pursuit - and is rotated out only after {@code Pursuit.Max_Ticks} AI ticks stuck while nobody in
 * its squad sees the target, or beyond {@code Pursuit.Max_Distance}.
 */
@DisplayName("PursuingBehavior - squad pursuit and the rotation rule")
class PursuingBehaviorTest {

	private static final double CUFF_RADIUS  = 3.0;
	private static final double ALERT_RANGE  = 40.0;
	private static final double MAX_DISTANCE = 80.0;
	private static final int    MAX_TICKS    = 120;

	private BukkitStatics    bukkit;
	private CopNpc           cop;
	private Player           player;
	private NpcSquad         squad;
	private PursuingBehavior behavior;
	private CuffLockRegistry cuffLocks;
	private final AtomicLong now = new AtomicLong(1_000);

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();

		UUID playerId = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.isValid()).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		squad = new NpcSquad();
		cop   = mock(CopNpc.class);
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		when(cop.squadFor(player)).thenReturn(squad);
		when(cop.distanceTo((LivingEntity) player)).thenReturn(20.0);

		cuffLocks = new CuffLockRegistry();
		behavior  = new PursuingBehavior(CUFF_RADIUS, ALERT_RANGE, MAX_DISTANCE, MAX_TICKS, mock(DetainmentService.class),
		                                 cuffLocks, RetreatSettings.DEFAULT, now::get);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("hopeless navigation no longer sends the cop back: it keeps pursuing with its squad (D1)")
	void hopelessNavigation_keepsPursuing() {
		when(cop.isNavigationHopeless()).thenReturn(true);
		when(cop.isNavigationStuck()).thenReturn(true);

		behavior.tick(cop);

		verify(cop, never()).transitionTo(any());
		verify(cop).pursue(player, squad, ALERT_RANGE);
	}

	@Test
	@DisplayName("stuck while nobody in the squad sees the target: reaching Max_Ticks rotates the cop out")
	void stuckAndUnseen_rotatesOutAtMaxTicks() {
		when(cop.isNavigationStuck()).thenReturn(true);
		when(cop.getPursuitTicks()).thenReturn(MAX_TICKS - 1);

		behavior.tick(cop);

		verify(cop).setPursuitTicks(MAX_TICKS);
		verify(cop).transitionTo(CopState.RETURNING);
		verify(cop, never()).pursue(any(), any(), anyDouble());
	}

	@Test
	@DisplayName("stuck while a squad member sees the target: the rotation count resets")
	void stuckButSeen_resetsCount() {
		squad.reportSighting(new Location(mock(World.class), 0, 64, 0));
		when(cop.isNavigationStuck()).thenReturn(true);
		when(cop.getPursuitTicks()).thenReturn(MAX_TICKS - 1);

		behavior.tick(cop);

		verify(cop).setPursuitTicks(0);
		verify(cop, never()).transitionTo(any());
		verify(cop).pursue(player, squad, ALERT_RANGE);
	}

	@Test
	@DisplayName("moving (not stuck): the rotation count resets")
	void notStuck_resetsCount() {
		when(cop.getPursuitTicks()).thenReturn(MAX_TICKS - 1);

		behavior.tick(cop);

		verify(cop).setPursuitTicks(0);
		verify(cop).pursue(player, squad, ALERT_RANGE);
	}

	@Test
	@DisplayName("a target beyond Pursuit.Max_Distance rotates the cop out")
	void beyondMaxDistance_rotatesOut() {
		when(cop.distanceTo((LivingEntity) player)).thenReturn(MAX_DISTANCE + 1);

		behavior.tick(cop);

		verify(cop).transitionTo(CopState.RETURNING);
		verify(cop, never()).pursue(any(), any(), anyDouble());
	}

	@Test
	@DisplayName("in cuff range while another officer holds the cuff lock: stay pursuing on the surround post, no CUFFING bounce")
	void lockHeldByOther_staysPursuing_noCuffingBounce() {
		inCuffRange();
		cuffLocks.tryAcquire(player.getUniqueId(), UUID.randomUUID());

		behavior.tick(cop);

		verify(cop, never()).transitionTo(any());
		verify(cop).pursue(player, squad, ALERT_RANGE);
	}

	@Test
	@DisplayName("in cuff range with the lock free: CUFFING")
	void lockFree_entersCuffing() {
		inCuffRange();

		behavior.tick(cop);

		verify(cop).transitionTo(CopState.CUFFING);
	}

	@Test
	@DisplayName("in cuff range once the suspect resisted (combat forced): COMBAT, even with the lock held")
	void combatForced_entersCombat() {
		inCuffRange();
		cuffLocks.tryAcquire(player.getUniqueId(), UUID.randomUUID());
		when(cop.isCombatForced()).thenReturn(true);

		behavior.tick(cop);

		verify(cop).transitionTo(CopState.COMBAT);
	}

	@Test
	@DisplayName("a badly hurt shooter breaks off to cover through its squad instead of holding its post (T-135)")
	void badlyHurtShooter_takesCover() {
		badlyHurtShooter();
		when(cop.takeCover(player, RetreatSettings.DEFAULT.radius(), squad)).thenReturn(NpcCoverStatus.MOVING);

		behavior.tick(cop);

		verify(cop).takeCover(player, RetreatSettings.DEFAULT.radius(), squad);
		verify(cop, never()).pursue(any(), any(), anyDouble());
		verify(cop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a badly hurt shooter with no cover in reach (open ground) keeps pursuing")
	void badlyHurtShooter_noCover_pursues() {
		badlyHurtShooter();
		when(cop.takeCover(player, RetreatSettings.DEFAULT.radius(), squad)).thenReturn(NpcCoverStatus.FAILED);

		behavior.tick(cop);

		verify(cop).pursue(player, squad, ALERT_RANGE);
	}

	@Test
	@DisplayName("a badly hurt melee cop does not fight while pursuing: it keeps chasing to cuff, no retreat")
	void badlyHurtMelee_keepsPursuing() {
		badlyHurtShooter();
		when(cop.isRangedAttacker()).thenReturn(false);

		behavior.tick(cop);

		verify(cop, never()).takeCover(any(), anyDouble(), any());
		verify(cop).pursue(player, squad, ALERT_RANGE);
		// a walk to cover begun in COMBAT ends on the hop here, so field care can treat it
		verify(cop).setMovingToCover(false);
	}

	@Test
	@DisplayName("a badly hurt shooter with the suspect in cuff range still cuffs: the retreat never breaks cuffing")
	void badlyHurtShooter_inCuffRange_stillCuffs() {
		badlyHurtShooter();
		inCuffRange();

		behavior.tick(cop);

		verify(cop).transitionTo(CopState.CUFFING);
		verify(cop, never()).takeCover(any(), anyDouble(), any());
	}

	@Test
	@DisplayName("the cover time limit holds in PURSUING and a new pursuit (onExit) gets a fresh retreat")
	void badlyHurtShooter_coverLimit_andResetOnExit() {
		badlyHurtShooter();
		when(cop.takeCover(player, RetreatSettings.DEFAULT.radius(), squad)).thenReturn(NpcCoverStatus.ARRIVED);

		behavior.tick(cop);
		now.addAndGet(CopRetreat.MAX_COVER_MS);
		behavior.tick(cop);
		verify(cop, times(1)).takeCover(any(), anyDouble(), any());
		verify(cop, times(1)).pursue(player, squad, ALERT_RANGE);

		behavior.onExit(cop);
		behavior.tick(cop);
		verify(cop, times(2)).takeCover(any(), anyDouble(), any());
	}

	@Test
	@DisplayName("a medic with a patient still fires while pursuing but neither pursues nor retreats")
	void medicWithPatient_firesButLeavesMovementToFieldCare() {
		badlyHurtShooter();
		when(cop.hasLineOfSight(player)).thenReturn(true);
		when(cop.canAttack()).thenReturn(true);
		when(cop.getPatient()).thenReturn(mock(CopNpc.class));

		behavior.tick(cop);

		verify(cop).attack(player);
		verify(cop, never()).pursue(any(), any(), anyDouble());
		verify(cop, never()).takeCover(any(), anyDouble(), any());
	}

	@Test
	@DisplayName("a melee patient under care holds still too (melee cops never retreat, so this is its only stop)")
	void meleePatientUnderCare_holdsStill() {
		when(cop.isUnderCare()).thenReturn(true);

		behavior.tick(cop);

		verify(cop).pauseNavigation();
		verify(cop, never()).pursue(any(), any(), anyDouble());
	}

	@Test
	@DisplayName("a suspect on the post-escape search is never cuffed, even in cuff range with the lock free (0.16.1 T-187)")
	void searchingTarget_isNotCuffed() {
		PursuingBehavior searching = new PursuingBehavior(CUFF_RADIUS, ALERT_RANGE, MAX_DISTANCE, MAX_TICKS,
		                                                  mock(DetainmentService.class), cuffLocks,
		                                                  RetreatSettings.DEFAULT, now::get,
		                                                  id -> id.equals(player.getUniqueId()));
		inCuffRange();

		searching.tick(cop);

		verify(cop, never()).transitionTo(CopState.CUFFING);
	}

	private void badlyHurtShooter() {
		LivingEntity self = mock(LivingEntity.class);
		when(self.getHealth()).thenReturn(5.0);
		when(self.getMaxHealth()).thenReturn(20.0);
		when(cop.getEntity()).thenReturn(self);
		when(cop.isRangedAttacker()).thenReturn(true);
	}

	private void inCuffRange() {
		when(cop.distanceTo((LivingEntity) player)).thenReturn(CUFF_RADIUS - 1);
		when(cop.hasLineOfSight(player)).thenReturn(true);
		when(cop.getTierConfig()).thenReturn(mock(CopTierConfig.class));
		net.citizensnpcs.api.npc.NPC npc = mock(net.citizensnpcs.api.npc.NPC.class);
		when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
		when(cop.getNpc()).thenReturn(npc);
	}
}
