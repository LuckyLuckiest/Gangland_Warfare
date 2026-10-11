package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.npc.NpcFanPlacement;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CivilianDamageListener - a Shield guard blocks part of a frontal hit")
class CivilianDamageListenerShieldTest {

	private static final NpcRole SHIELD = new NpcRole("Shield", "Shield", NpcFanPlacement.CENTER, null, null, 1.0, 0,
	                                                  null, 1.0, 0, null, 0.5, 90.0, false, false);

	private final CivilianService        service  = mock(CivilianService.class);
	private final CivilianDamageListener listener = new CivilianDamageListener(service);
	private final LivingEntity           victim   = mock(LivingEntity.class);

	@Test
	@DisplayName("a hit from in front is halved; one from 120 degrees off or behind is not")
	void frontalHit_reduced_sideHit_full() {
		guard(SHIELD);

		assertEquals(5.0, hit(attackerAt(0, 10)).getDamage(), 1e-9);
		assertEquals(10.0, hit(attackerAt(Math.sin(Math.toRadians(120)) * 10, Math.cos(Math.toRadians(120)) * 10))
				.getDamage(), 1e-9);
		assertEquals(10.0, hit(attackerAt(0, -10)).getDamage(), 1e-9);
	}

	@Test
	@DisplayName("a civilian with no role takes the full hit")
	void noRole_full() {
		guard(NpcRole.NONE);
		assertEquals(10.0, hit(attackerAt(0, 10)).getDamage(), 1e-9);
	}

	@Test
	@DisplayName("an arrow is judged by where its shooter stands, not where it lands")
	void projectileShooterResolved() {
		guard(SHIELD);
		Arrow arrow = mock(Arrow.class);
		when(arrow.getLocation()).thenReturn(new Location(null, 0, 64, -0.5)); // lands on the guard's back
		Player shooter = attackerAt(0, 10);
		when(arrow.getShooter()).thenReturn(shooter);

		assertEquals(5.0, hit(arrow).getDamage(), 1e-9);
	}

	private void guard(NpcRole role) {
		UUID        id  = UUID.randomUUID();
		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.getRole()).thenReturn(role);
		when(victim.getUniqueId()).thenReturn(id);
		when(victim.getLocation()).thenReturn(new Location(null, 0, 64, 0, 0f, 0f)); // yaw 0 faces +Z
		when(service.getNpc(id)).thenReturn(npc);
	}

	private static Player attackerAt(double x, double z) {
		Player player = mock(Player.class);
		when(player.getLocation()).thenReturn(new Location(null, x, 64, z));
		return player;
	}

	@SuppressWarnings("deprecation")
	private EntityDamageByEntityEvent hit(Entity damager) {
		EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, victim, DamageCause.ENTITY_ATTACK,
		                                                                10.0);
		listener.onShieldHit(event);
		return event;
	}
}
