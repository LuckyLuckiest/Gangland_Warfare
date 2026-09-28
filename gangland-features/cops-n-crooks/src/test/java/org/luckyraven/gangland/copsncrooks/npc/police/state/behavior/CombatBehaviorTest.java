package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9): COMBAT keeps its attacks and replaces the hold / hopeless / pursuit branch with one
 * {@code cop.pursue(target, squad, Alert_Range)}; an entity target (hostile NPC) uses the cop's private squad.
 */
@DisplayName("CombatBehavior - navigation through the squad pursuit")
class CombatBehaviorTest {

	private static final double COMBAT_RANGE = 4.0;
	private static final double ALERT_RANGE  = 40.0;

	private BukkitStatics  bukkit;
	private CopNpc         cop;
	private CombatBehavior behavior;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		cop    = mock(CopNpc.class);
		when(cop.getTierConfig()).thenReturn(mock(CopTierConfig.class));
		behavior = new CombatBehavior(COMBAT_RANGE, ALERT_RANGE, mock(DetainmentService.class), RetreatSettings.DEFAULT);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a player target is attacked as before and pursued with the cop's squad for it")
	void playerTarget_attacksAndPursuesWithSquad() {
		UUID   playerId = UUID.randomUUID();
		Player player   = mock(Player.class);
		when(player.isValid()).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
		NpcSquad squad = new NpcSquad();
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		when(cop.squadFor(player)).thenReturn(squad);
		when(cop.distanceTo((LivingEntity) player)).thenReturn(2.0);
		when(cop.canAttack()).thenReturn(true);
		when(cop.hasLineOfSight((LivingEntity) player)).thenReturn(true);

		behavior.tick(cop);

		verify(cop).attack(player);
		verify(cop).pursue(player, squad, ALERT_RANGE);
		verify(cop, never()).navigateTo(any());
		verify(cop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("an entity target (hostile NPC) is pursued with the cop's own squad")
	void entityTarget_pursuesWithPrivateSquad() {
		LivingEntity civilian = mock(LivingEntity.class);
		when(civilian.isValid()).thenReturn(true);
		when(cop.getTargetEntity()).thenReturn(civilian);
		when(cop.distanceTo(civilian)).thenReturn(30.0);
		NpcSquad squad = new NpcSquad();
		when(cop.squadFor(civilian)).thenReturn(squad);

		behavior.tick(cop);

		verify(cop).pursue(civilian, squad, ALERT_RANGE);
		verify(cop, never()).navigateTo(any());
	}
	@Test
	@DisplayName("a ranged cop holding its band at 13 blocks still fires: the gate is its sight range, not 3x Combat_Range")
	void rangedCopInHoldAt13Blocks_stillAttacks() {
		Player player = targetAt(13.0);
		when(cop.isRangedAttacker()).thenReturn(true);

		behavior.tick(cop);

		verify(cop).attack(player);
	}

	@Test
	@DisplayName("a ranged cop beyond Alert_Range does not fire")
	void rangedCopBeyondAlertRange_doesNotAttack() {
		targetAt(41.0);
		when(cop.isRangedAttacker()).thenReturn(true);

		behavior.tick(cop);

		verify(cop, never()).attack(any(Player.class));
	}

	@Test
	@DisplayName("a melee tier (weapons flag on, but no ranged attack) swings only within Combat_Range")
	void meleeTierWithWeaponsFlag_usesCombatRange() {
		Player player = targetAt(6.0);
		when(cop.getTierConfig().canUseWeapons()).thenReturn(true);
		when(cop.isRangedAttacker()).thenReturn(false);

		behavior.tick(cop);

		verify(cop, never()).attack(any(Player.class));

		when(cop.distanceTo((LivingEntity) player)).thenReturn(3.0);
		behavior.tick(cop);

		verify(cop).attack(player);
	}

	@Test
	@DisplayName("badly hurt (at or below Retreat.Health_Fraction): the cop takes cover instead of pursuing")
	void badlyHurt_takesCover() {
		Player       player = targetAt(10.0);
		LivingEntity self   = mock(LivingEntity.class);
		when(self.getHealth()).thenReturn(5.0);
		when(self.getMaxHealth()).thenReturn(20.0);
		when(cop.getEntity()).thenReturn(self);

		behavior.tick(cop);

		verify(cop).takeCover(player, RetreatSettings.DEFAULT.radius());
		verify(cop, never()).pursue(any(), any(), anyDouble());
	}

	@Test
	@DisplayName("healthy, or with retreat disabled: the cop keeps pursuing")
	void healthyOrRetreatDisabled_pursues() {
		Player       player = targetAt(10.0);
		LivingEntity self   = mock(LivingEntity.class);
		when(self.getHealth()).thenReturn(5.0);
		when(self.getMaxHealth()).thenReturn(20.0);
		when(cop.getEntity()).thenReturn(self);
		behavior = new CombatBehavior(COMBAT_RANGE, ALERT_RANGE, mock(DetainmentService.class),
		                              new RetreatSettings(false, 0.3, 12.0));

		behavior.tick(cop);

		verify(cop, never()).takeCover(any(), anyDouble());
		verify(cop).pursue(eq(player), any(), eq(ALERT_RANGE));
	}

	private Player targetAt(double distance) {
		UUID   playerId = UUID.randomUUID();
		Player player   = mock(Player.class);
		when(player.isValid()).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		when(cop.squadFor(player)).thenReturn(new NpcSquad());
		when(cop.distanceTo((LivingEntity) player)).thenReturn(distance);
		when(cop.canAttack()).thenReturn(true);
		when(cop.hasLineOfSight((LivingEntity) player)).thenReturn(true);
		return player;
	}
}
