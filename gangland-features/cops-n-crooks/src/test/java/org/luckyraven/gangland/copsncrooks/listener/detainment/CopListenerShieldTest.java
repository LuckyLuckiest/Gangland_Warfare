package org.luckyraven.gangland.copsncrooks.listener.detainment;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.raytrace.WeaponRaytracer;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CopListener - the Defender's shield halves hits from its front cone, melee and Bartizan gunfire alike")
class CopListenerShieldTest {

	private static final CopRole DEFENDER = new CopRole("Defender", "Defender", NpcFanPlacement.CENTER, 4.0, 7.0, 1.5,
	                                                    new ItemStack(Material.SHIELD), 0, 0.0, 1.0, 0, null, 0.5, 60,
	                                                    false, false);

	private final CopManager   copManager = mock(CopManager.class);
	private final CopListener  listener   = new CopListener(copManager);
	private final LivingEntity victim     = mock(LivingEntity.class);
	private final BukkitStatics bukkit    = BukkitStatics.install(); // NpcSupport.isNpc reads the plugin manager

	@AfterEach
	void tearDown() {
		WeaponRaytracer.setRaytraceDamageInProgress(false);
		bukkit.close();
	}

	@Test
	@DisplayName("a melee hit from in front is halved; one from behind is not")
	void melee_frontHalved_behindFull() {
		defender(DEFENDER);

		assertEquals(5.0, hit(attackerAt(0, 10)).getDamage(), 1e-9);
		assertEquals(10.0, hit(attackerAt(0, -10)).getDamage(), 1e-9);
	}

	@Test
	@DisplayName("Bartizan's gunfire (damage() while a raytrace is in progress) is blocked by the same guard")
	void raytraceDamage_frontHalved() {
		defender(DEFENDER);
		WeaponRaytracer.setRaytraceDamageInProgress(true);

		assertEquals(5.0, hit(attackerAt(2, 10)).getDamage(), 1e-9);
	}

	@Test
	@DisplayName("an arrow is judged by where its shooter stands, not where it lands")
	void projectile_judgedByShooter() {
		defender(DEFENDER);
		Arrow arrow = mock(Arrow.class);
		when(arrow.getLocation()).thenReturn(new Location(null, 0, 64, -0.5)); // lands on the cop's back
		Player shooter = attackerAt(0, 10);
		when(arrow.getShooter()).thenReturn(shooter);

		assertEquals(5.0, hit(arrow).getDamage(), 1e-9);
	}

	@Test
	@DisplayName("a cop with no role, or a role with no Block_Fraction, takes the full hit")
	void noRole_fullDamage() {
		defender(null);

		assertEquals(10.0, hit(attackerAt(0, 10)).getDamage(), 1e-9);
	}

	private void defender(CopRole role) {
		CopNpc cop = mock(CopNpc.class);
		when(cop.getRole()).thenReturn(role);
		when(victim.getLocation()).thenReturn(new Location(null, 0, 64, 0, 0f, 0f)); // yaw 0 faces +Z
		when(copManager.isCopNpc(victim)).thenReturn(true);
		when(copManager.findCopByEntity(victim)).thenReturn(cop);
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
		listener.onCopDamaged(event);
		return event;
	}
}
