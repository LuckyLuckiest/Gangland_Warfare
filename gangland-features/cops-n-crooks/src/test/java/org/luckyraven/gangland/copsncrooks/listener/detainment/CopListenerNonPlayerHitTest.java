package org.luckyraven.gangland.copsncrooks.listener.detainment;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.projectiles.BlockProjectileSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.raytrace.WeaponRaytracer;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterisation of {@code CopListener#onCopDamaged} when the damager is not a (real) player: another cop's attack,
 * or a projectile of one, is cancelled as friendly fire; any other living damager, or the living shooter of a
 * projectile, is queued on the hit cop as an entity attacker; anything else is dropped. Nothing is alerted.
 */
@DisplayName("CopListener - a cop hit by something that is not a player")
class CopListenerNonPlayerHitTest {

	private final CopManager    copManager = mock(CopManager.class);
	private final CopListener   listener   = new CopListener(copManager, mock(HeatLedger.class), mock(CopCrimeReports.class));
	private final LivingEntity  victim     = mock(LivingEntity.class);
	private final CopNpc        hitCop     = mock(CopNpc.class);
	private final BukkitStatics bukkit     = BukkitStatics.install(); // NpcSupport.isNpc reads the plugin manager

	@AfterEach
	void tearDown() {
		WeaponRaytracer.setRaytraceDamageInProgress(false);
		bukkit.close();
	}

	private EntityDamageByEntityEvent hit(Entity damager) {
		when(copManager.isCopNpc(victim)).thenReturn(true);
		when(copManager.findCopByEntity(victim)).thenReturn(hitCop);
		@SuppressWarnings("deprecation")
		EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, victim, DamageCause.ENTITY_ATTACK, 10.0);
		listener.onCopDamaged(event);
		return event;
	}

	private Arrow arrowFrom(Object shooter) {
		Arrow arrow = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn((org.bukkit.projectiles.ProjectileSource) shooter);
		return arrow;
	}

	@Test
	@DisplayName("another cop's attack is cancelled and nobody is queued")
	void copDamager_isCancelled() {
		LivingEntity otherCop = mock(LivingEntity.class);
		when(copManager.isCopNpc(otherCop)).thenReturn(true);

		EntityDamageByEntityEvent event = hit(otherCop);

		assertTrue(event.isCancelled());
		verify(hitCop, never()).addEntityAttacker(any());
		// the shield check looked the cop up once; the friendly-fire branch returns before the bookkeeping lookup
		verify(copManager, times(1)).findCopByEntity(victim);
	}

	@Test
	@DisplayName("a projectile shot by another cop is cancelled and nobody is queued")
	void copShooter_isCancelled() {
		LivingEntity otherCop = mock(LivingEntity.class);
		when(copManager.isCopNpc(otherCop)).thenReturn(true);

		EntityDamageByEntityEvent event = hit(arrowFrom(otherCop));

		assertTrue(event.isCancelled());
		verify(hitCop, never()).addEntityAttacker(any());
		verify(copManager, times(1)).findCopByEntity(victim);
	}

	@Test
	@DisplayName("a living non-cop damager is queued on the hit cop and the hit stands")
	void livingDamager_isQueued() {
		LivingEntity zombie = mock(LivingEntity.class);

		EntityDamageByEntityEvent event = hit(zombie);

		assertFalse(event.isCancelled());
		verify(hitCop).addEntityAttacker(zombie);
		verify(copManager, times(2)).findCopByEntity(victim);
	}

	@Test
	@DisplayName("a projectile with a living non-cop shooter queues the shooter")
	void livingShooter_isQueued() {
		LivingEntity skeleton = mock(LivingEntity.class);

		EntityDamageByEntityEvent event = hit(arrowFrom(skeleton));

		assertFalse(event.isCancelled());
		verify(hitCop).addEntityAttacker(skeleton);
	}

	@Test
	@DisplayName("a damager that is not living, and a projectile from a block, queue nobody")
	void notLiving_isDropped() {
		assertFalse(hit(mock(Entity.class)).isCancelled());
		assertFalse(hit(arrowFrom(mock(BlockProjectileSource.class))).isCancelled());

		verify(hitCop, never()).addEntityAttacker(any());
	}

	@Test
	@DisplayName("when the cop cannot be found for the bookkeeping, nothing is queued and nothing throws")
	void unknownCop_isHarmless() {
		LivingEntity zombie = mock(LivingEntity.class);
		when(copManager.isCopNpc(victim)).thenReturn(true);
		when(copManager.findCopByEntity(victim)).thenReturn(null);
		@SuppressWarnings("deprecation")
		EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(zombie, victim, DamageCause.ENTITY_ATTACK, 10.0);

		listener.onCopDamaged(event);

		assertFalse(event.isCancelled());
		verifyNoMoreInteractions(hitCop);
	}
}
