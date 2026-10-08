package org.luckyraven.gangland.copsncrooks.listener.turf;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.turf.npc.guard.TurfCopGuard;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Red tests for T-189 (0.16.1): a cop NPC that damages a player hands that player to {@link TurfCopGuard}. Covers
 * the melee path ({@link EntityDamageByEntityEvent}), the projectile-with-cop-shooter path, the Bartizan raytrace path
 * ({@link WeaponRaytraceImpactEvent}), and the ignore rules for non-cop attackers and NPC victims.
 */
@DisplayName("TurfCopProtectionListener — cop hits on players reach the turf guard (T-189)")
class TurfCopProtectionListenerTest {

	private CopManager                copManager;
	private TurfCopGuard              guard;
	private TurfCopProtectionListener listener;
	private MockedStatic<NpcSupport>  npcSupport;

	@BeforeEach
	void setUp() {
		copManager = mock(CopManager.class);
		guard      = mock(TurfCopGuard.class);
		listener   = new TurfCopProtectionListener(copManager, guard);

		npcSupport = mockStatic(NpcSupport.class);
		npcSupport.when(() -> NpcSupport.isNpc(any())).thenReturn(false);
	}

	@AfterEach
	void tearDown() {
		npcSupport.close();
	}

	@Test
	@DisplayName("melee: a cop NPC damaging a player calls guard.onCopHitPlayer(cop, player)")
	void melee_copHitsPlayer_callsGuard() {
		LivingEntity cop    = mock(LivingEntity.class);
		Player       victim = mock(Player.class);
		when(copManager.isCopNpc(cop)).thenReturn(true);

		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(cop);
		when(event.getEntity()).thenReturn(victim);

		listener.onDamage(event);

		verify(guard).onCopHitPlayer(cop, victim);
	}

	@Test
	@DisplayName("projectile: a cop-shot arrow resolves the attacker to the cop shooter")
	void projectile_copShooter_resolvesToCop() {
		LivingEntity cop    = mock(LivingEntity.class);
		Player       victim = mock(Player.class);
		Projectile   arrow  = mock(Projectile.class);
		when(arrow.getShooter()).thenReturn(cop);
		when(copManager.isCopNpc(cop)).thenReturn(true);

		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(arrow);
		when(event.getEntity()).thenReturn(victim);

		listener.onDamage(event);

		verify(guard).onCopHitPlayer(cop, victim);
	}

	@Test
	@DisplayName("non-cop attacker: the guard is never called")
	void nonCopAttacker_ignored() {
		LivingEntity criminal = mock(LivingEntity.class);
		Player       victim   = mock(Player.class);
		when(copManager.isCopNpc(criminal)).thenReturn(false);

		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(criminal);
		when(event.getEntity()).thenReturn(victim);

		listener.onDamage(event);

		verify(guard, never()).onCopHitPlayer(any(), any());
	}

	@Test
	@DisplayName("NPC victim (Citizens-backed Player entity): the guard is never called")
	void npcVictim_ignored() {
		LivingEntity cop      = mock(LivingEntity.class);
		Player       npcBody  = mock(Player.class);
		when(copManager.isCopNpc(cop)).thenReturn(true);
		npcSupport.when(() -> NpcSupport.isNpc(npcBody)).thenReturn(true);

		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(cop);
		when(event.getEntity()).thenReturn(npcBody);

		listener.onDamage(event);

		verify(guard, never()).onCopHitPlayer(any(), any());
	}

	@Test
	@DisplayName("raytrace: a Bartizan weapon impact from a cop on a player calls the guard")
	void raytrace_copShooter_callsGuard() {
		LivingEntity cop    = mock(LivingEntity.class);
		Player       victim = mock(Player.class);
		when(copManager.isCopNpc(cop)).thenReturn(true);

		WeaponRaytraceImpactEvent event = mock(WeaponRaytraceImpactEvent.class);
		when(event.getShooter()).thenReturn(cop);
		when(event.getHitEntity()).thenReturn(victim);

		listener.onWeaponImpact(event);

		verify(guard).onCopHitPlayer(cop, victim);
	}
}
