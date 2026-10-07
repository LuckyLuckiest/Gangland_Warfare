package org.luckyraven.gangland.listener.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.teleportation.HospitalShield;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the hospital respawn shield does to damage: a shielded victim's hits are cancelled (void excepted), attacking
 * anything ends the shield, and quitting ends it too. The shield itself is a mock whose state the test sets.
 */
@DisplayName("HospitalShieldListener - the shield against damage")
class HospitalShieldListenerTest {

	private HospitalShield         shield;
	private HospitalShieldListener listener;
	private Player                 victim;
	private UUID                   victimId;

	@BeforeEach
	void setUp() {
		shield   = mock(HospitalShield.class);
		listener = new HospitalShieldListener(shield);
		victimId = UUID.randomUUID();
		victim   = mock(Player.class);
		when(victim.getUniqueId()).thenReturn(victimId);
	}

	private EntityDamageEvent plain(Player who, EntityDamageEvent.DamageCause cause) {
		EntityDamageEvent event = mock(EntityDamageEvent.class);
		when(event.getEntity()).thenReturn(who);
		when(event.getCause()).thenReturn(cause);
		return event;
	}

	private EntityDamageByEntityEvent byEntity(Entity victimEntity, Entity damager) {
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getEntity()).thenReturn(victimEntity);
		when(event.getDamager()).thenReturn(damager);
		when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
		return event;
	}

	@Test
	@DisplayName("a shielded victim has plain and attacked damage cancelled; an unshielded one does not")
	void blocksDamage() {
		when(shield.isShielded(victimId)).thenReturn(true);
		EntityDamageEvent         fall = plain(victim, EntityDamageEvent.DamageCause.FALL);
		EntityDamageByEntityEvent bite = byEntity(victim, mock(Zombie.class));

		listener.onDamage(fall);
		listener.onDamage(bite);

		verify(fall).setCancelled(true);
		verify(bite).setCancelled(true);

		when(shield.isShielded(victimId)).thenReturn(false);
		EntityDamageEvent open = plain(victim, EntityDamageEvent.DamageCause.FALL);

		listener.onDamage(open);

		verify(open, never()).setCancelled(true);
	}

	@Test
	@DisplayName("the shielded player hitting a zombie ends his shield and the zombie damage is not cancelled")
	void attackingEndsIt() {
		Zombie zombie = mock(Zombie.class);
		when(shield.isShielded(victimId)).thenReturn(true);
		EntityDamageByEntityEvent hit = byEntity(zombie, victim);

		listener.onDamage(hit);

		verify(shield).end(victimId);
		verify(hit, never()).setCancelled(true);

		// the same through his arrow
		Projectile arrow = mock(Projectile.class);
		when(arrow.getShooter()).thenReturn(victim);
		EntityDamageByEntityEvent shot = byEntity(zombie, arrow);

		listener.onDamage(shot);

		verify(shield, times(2)).end(victimId);
		verify(shot, never()).setCancelled(true);
	}

	@Test
	@DisplayName("void damage is never blocked")
	void voidDamage_isNotBlocked() {
		when(shield.isShielded(victimId)).thenReturn(true);
		EntityDamageEvent fall = plain(victim, EntityDamageEvent.DamageCause.VOID);

		listener.onDamage(fall);

		verify(fall, never()).setCancelled(true);
	}

	@Test
	@DisplayName("quitting ends the shield")
	void quit_endsIt() {
		PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(victim);

		listener.onQuit(quit);

		verify(shield).end(victimId);
	}

}
