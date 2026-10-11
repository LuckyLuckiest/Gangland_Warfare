package org.luckyraven.gangland.fights.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightState;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FightDamageListener - a lethal hit in a fight ends the fight, never the player (GP-31, GP-41)")
class FightDamageListenerTest {

	private FightRig            rig;
	private FightDamageListener listener;

	@BeforeEach
	void setUp() {
		rig      = new FightRig();
		listener = new FightDamageListener(rig.manager, rig.arenas, rig.rules);
		when(rig.a.getLocation()).thenReturn(rig.at(2, 1, 2));
		when(rig.b.getLocation()).thenReturn(rig.at(18, 1, 18));
	}

	private static EntityDamageEvent environment(FightRig rig, DamageCause cause) {
		return new EntityDamageEvent(rig.a, cause, 1000D);
	}

	@Test
	@DisplayName("it runs at NORMAL, below the impl HIGH and HIGHEST death handlers, and skips already-cancelled hits")
	void priority_isNormalAndIgnoresCancelled() throws Exception {
		EventHandler handler = FightDamageListener.class.getMethod("onDamage", EntityDamageEvent.class)
		                                                .getAnnotation(EventHandler.class);
		assertEquals(EventPriority.NORMAL, handler.priority());
		assertTrue(handler.ignoreCancelled());
	}

	@Test
	@DisplayName("every lethal source (hand, projectile, Bartizan-style custom cause, VOID, FALL) is cancelled and eliminates the fighter")
	void everyLethalSource_intercepted() {
		Function<FightRig, EntityDamageEvent> hand = r -> new EntityDamageByEntityEvent(r.b, r.a, DamageCause.ENTITY_ATTACK, 1000D);
		Function<FightRig, EntityDamageEvent> projectile = r -> {
			Projectile arrow = mock(Projectile.class);
			when(arrow.getShooter()).thenReturn(r.b);
			return new EntityDamageByEntityEvent(arrow, r.a, DamageCause.PROJECTILE, 1000D);
		};
		Function<FightRig, EntityDamageEvent> custom = r -> new EntityDamageByEntityEvent(r.b, r.a, DamageCause.CUSTOM, 1000D);
		Function<FightRig, EntityDamageEvent> voidFall = r -> environment(r, DamageCause.VOID);
		Function<FightRig, EntityDamageEvent> fall = r -> environment(r, DamageCause.FALL);

		for (Function<FightRig, EntityDamageEvent> source : java.util.List.of(hand, projectile, custom, voidFall, fall)) {
			FightRig           fresh = new FightRig();
			FightDamageListener fl   = new FightDamageListener(fresh.manager, fresh.arenas, fresh.rules);
			Fight              fight = fresh.running(0);
			EntityDamageEvent  event = source.apply(fresh);

			fl.onDamage(event);

			assertTrue(event.isCancelled(), event.getCause() + " must be cancelled");
			assertTrue(fight.eliminated(fresh.a.getUniqueId()), event.getCause() + " must eliminate the victim");
			verify(fresh.fightWorld).restore(any());
		}
	}

	@Test
	@DisplayName("a lethal hit on the last fighter of a side ends the fight with the other side winning")
	void lethalHit_decidesTheFight() {
		Fight fight = rig.running(0);

		listener.onDamage(new EntityDamageByEntityEvent(rig.b, rig.a, DamageCause.ENTITY_ATTACK, 1000D));

		assertEquals(FightState.RESULT, fight.state());
	}

	@Test
	@DisplayName("a hit that leaves the fighter alive passes untouched")
	void nonLethal_passes() {
		Fight fight = rig.running(0);
		EntityDamageEvent event = new EntityDamageByEntityEvent(rig.b, rig.a, DamageCause.ENTITY_ATTACK, 3D);

		listener.onDamage(event);

		assertFalse(event.isCancelled());
		assertFalse(fight.eliminated(rig.a.getUniqueId()));
	}

	private PlayerInventory holdTotem() {
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(rig.a.getInventory()).thenReturn(inventory);
		when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.TOTEM_OF_UNDYING));
		return inventory;
	}

	@Test
	@DisplayName("a totem is ignored by default: the lethal hit is still intercepted")
	void totem_ignoredByDefault() {
		rig.running(0);
		holdTotem();

		EntityDamageEvent event = environment(rig, DamageCause.FALL);
		listener.onDamage(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("with Respect_Totems on the totem is consumed and saves him: cancelled, not eliminated")
	void totem_honouredWhenEnabled() {
		Fight fight = rig.running(0);
		PlayerInventory inventory = holdTotem();
		when(rig.rules.isRespectTotems()).thenReturn(true);

		EntityDamageEvent event = environment(rig, DamageCause.FALL);
		listener.onDamage(event);

		assertTrue(event.isCancelled());
		assertFalse(fight.eliminated(rig.a.getUniqueId()));
		assertEquals(FightState.RUNNING, fight.state());
		verify(inventory).setItemInMainHand(null);
		verify(rig.a).setHealth(1.0D);
	}

	@Test
	@DisplayName("countdown and result make fighters immune to everything")
	void countdownImmune() {
		Fight fight = rig.countdown(0);
		EntityDamageEvent hit = new EntityDamageByEntityEvent(rig.b, rig.a, DamageCause.ENTITY_ATTACK, 1D);

		listener.onDamage(hit);

		assertTrue(hit.isCancelled());

		fight.state(FightState.RESULT);
		EntityDamageEvent fall = environment(rig, DamageCause.FALL);
		listener.onDamage(fall);

		assertTrue(fall.isCancelled());
		assertFalse(fight.eliminated(rig.a.getUniqueId()));
	}

	@Test
	@DisplayName("an outsider hitting a fighter, a fighter hitting a bystander and any hit on a bystander inside a busy arena are cancelled")
	void outsiderHit_cancelled() {
		rig.running(0);
		Player outsider = rig.player();
		when(outsider.getLocation()).thenReturn(rig.at(100, 1, 100));
		Player inside = rig.player();
		when(inside.getLocation()).thenReturn(rig.at(10, 1, 10));

		EntityDamageEvent outsiderHitsFighter  = new EntityDamageByEntityEvent(outsider, rig.a, DamageCause.ENTITY_ATTACK, 1D);
		EntityDamageEvent fighterHitsBystander = new EntityDamageByEntityEvent(rig.a, outsider, DamageCause.ENTITY_ATTACK, 1D);
		EntityDamageEvent bystanderInsideHurt  = new EntityDamageEvent(inside, DamageCause.FALL, 1D);
		EntityDamageEvent bystanderFarAway     = new EntityDamageEvent(outsider, DamageCause.FALL, 1D);
		listener.onDamage(outsiderHitsFighter);
		listener.onDamage(fighterHitsBystander);
		listener.onDamage(bystanderInsideHurt);
		listener.onDamage(bystanderFarAway);

		assertTrue(outsiderHitsFighter.isCancelled());
		assertTrue(fighterHitsBystander.isCancelled());
		assertTrue(bystanderInsideHurt.isCancelled());
		assertFalse(bystanderFarAway.isCancelled());
	}
}
