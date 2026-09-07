package org.luckyraven.gangland.weapon.projectile;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.dto.ModifiersData;
import org.luckyraven.gangland.weapon.modifiers.action.PenetrationModifier;
import org.luckyraven.gangland.weapon.modifiers.action.RicochetModifier;
import org.luckyraven.gangland.weapon.support.WeaponFixtures;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link ProjectileState}'s per-shot damage-multiplier and penetration/ricochet-budget bookkeeping (weapons.md
 * W17 — Modifiers: penetration, ricochet, block break, tracer, AP, flat damage).
 *
 * <p>Pins Observation #7 (weapons.md): {@code canPenetrateBlock}/{@code canPenetrateEntity}/{@code canRicochet}
 * dereference {@code weapon.getModifiersData()} with no null check. {@code ModifiersData} is only populated by
 * {@code ModifiersSectionParser} when a weapon YAML has a {@code Modifiers:} section — a weapon built without one
 * (as every {@code WeaponFixtures} factory intentionally leaves it, matching an admin-authored file that omits
 * {@code Modifiers:}) throws {@link NullPointerException} on first use, not a graceful "no modifiers" no-op.
 */
@DisplayName("ProjectileState — damage multiplier and penetration/ricochet budget")
class ProjectileStateTest {

	@Test
	@DisplayName("getCurrentDamage starts at baseDamage and reflects the accumulated multiplier")
	void getCurrentDamage_startsAtBaseDamage() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ProjectileState state = new ProjectileState(weapon, 10.0);

		assertEquals(10.0, state.getCurrentDamage());
	}

	@Test
	@DisplayName("single-arg constructor reads baseDamage from the gun's ProjectileData")
	void constructor_gunOverload_readsProjectileDamage() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1); // ProjectileData.damage = 5.0, see WeaponFixtures
		ProjectileState state = new ProjectileState(weapon);

		assertEquals(5.0, state.getCurrentDamage());
	}

	@Test
	@DisplayName("applyPenetrationReduction multiplies damage by (1 - reduction), compounding across calls")
	void applyPenetrationReduction_compounds() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ProjectileState state = new ProjectileState(weapon, 100.0);

		state.applyPenetrationReduction(0.5); // 50% lost -> 50.0
		assertEquals(50.0, state.getCurrentDamage());

		state.applyPenetrationReduction(0.5); // another 50% lost -> 25.0
		assertEquals(25.0, state.getCurrentDamage());
	}

	@Test
	@DisplayName("applyRicochetReduction multiplies damage by the retention fraction")
	void applyRicochetReduction_multipliesByRetention() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ProjectileState state = new ProjectileState(weapon, 100.0);

		state.applyRicochetReduction(0.75);

		assertEquals(75.0, state.getCurrentDamage());
	}

	@Test
	@DisplayName("canPenetrateBlock/Entity — true below budget, false once the modifier's limit is reached")
	void canPenetrate_gatedByBudget() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.setPenetration(new PenetrationModifier(2, 1, 0.2));
		weapon.setModifiersData(modifiers);

		ProjectileState state = new ProjectileState(weapon, 10.0);

		assertTrue(state.canPenetrateBlock());
		assertTrue(state.canPenetrateEntity());

		state.setBlocksPenetrated(2); // reached the 2-block budget
		assertFalse(state.canPenetrateBlock());

		state.setEntitiesPenetrated(1); // reached the 1-entity budget
		assertFalse(state.canPenetrateEntity());
	}

	@Test
	@DisplayName("canPenetrateBlock/Entity are false when no Penetration modifier is configured")
	void canPenetrate_noPenetrationModifier_false() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		weapon.setModifiersData(new ModifiersData()); // present, but hasPenetration() == false

		ProjectileState state = new ProjectileState(weapon, 10.0);

		assertFalse(state.canPenetrateBlock());
		assertFalse(state.canPenetrateEntity());
	}

	@Test
	@DisplayName("canRicochet is gated by the highest maxBounces across every configured RicochetModifier")
	void canRicochet_gatedByHighestMaxBounces() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.addRicochet(new RicochetModifier(1, Set.of(Material.STONE), 0.5));
		modifiers.addRicochet(new RicochetModifier(3, Set.of(Material.GLASS), 0.8));
		weapon.setModifiersData(modifiers);

		ProjectileState state = new ProjectileState(weapon, 10.0);

		assertTrue(state.canRicochet());

		state.setBounceCount(2);
		assertTrue(state.canRicochet(), "the 3-bounce modifier still has budget even though the 1-bounce one doesn't");

		state.setBounceCount(3);
		assertFalse(state.canRicochet());
	}

	@Test
	@DisplayName("canPenetrateBlock/Entity/canRicochet throw NPE when the weapon has no Modifiers: section at all (Observation #7, weapons.md)")
	void canPenetrateAndRicochet_nullModifiersData_throwsNpe() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1); // no setModifiersData call — matches an admin YAML with
		                                                     // no `Modifiers:` section
		ProjectileState state = new ProjectileState(weapon, 10.0);

		assertThrows(NullPointerException.class, state::canPenetrateBlock);
		assertThrows(NullPointerException.class, state::canPenetrateEntity);
		assertThrows(NullPointerException.class, state::canRicochet);
	}

}
