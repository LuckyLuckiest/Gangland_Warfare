package org.luckyraven.gangland.weapon;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.dto.MeleeData;
import org.luckyraven.gangland.weapon.support.WeaponFixtures;
import org.luckyraven.gangland.weapon.types.WeaponType;
import org.luckyraven.gangland.weapon.types.biological.BiologicalWeapon;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;
import org.luckyraven.gangland.weapon.types.incendiary.IncendiaryWeapon;
import org.luckyraven.gangland.weapon.types.melee.MeleeWeapon;
import org.luckyraven.gangland.weapon.types.throwable.ThrowableWeapon;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins magazine-boundary behaviour ({@code consumeShot}/{@code addAmmunition}/{@code isMagazineFull}/
 * {@code isMagazineEmpty}/{@code requiresReload}) across all five weapon action types, per the project convention
 * that a weapon-system change must be checked against gun, melee, throwable, incendiary and biological — not just
 * {@code GunAction} (see {@code feedback_unify_across_weapon_types} and weapons.md's divergence table,
 * Observation #42).
 *
 * <p>Pins Observation #13 (weapons.md): {@code GunWeaponParser} defaults {@code Consumed_Amount} to {@code 0}
 * when absent, and {@link GunWeapon#consumeShot()} subtracts exactly that amount — a gun YAML omitting
 * {@code Consumed_Amount} therefore has an infinite magazine even though {@code consumeShot()} keeps returning
 * {@code true}.
 *
 * <p>Pins the first half of Observation #14 (weapons.md): {@link MeleeWeapon} and {@link ThrowableWeapon} never
 * override {@code consumeShot()}, so they fall back to {@link Weapon#consumeShot()}'s base "-1 per call" — which,
 * per W9/W10 in weapons.md, is never actually called by {@code MeleeAction}/{@code ThrowableAction}. This test
 * only proves what the base method itself would do if called; it does not claim melee/throwable weapons consume
 * ammunition in real play.
 */
@DisplayName("Weapon consumeShot/addAmmunition/magazine boundaries — across all five action types")
class WeaponConsumeShotTest {

	@Test
	@DisplayName("GunWeapon.consumeShot subtracts Projectile.Consumed_Amount, clamped at 0")
	void gunWeapon_consumeShot_subtractsConsumedAmount() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(10, 3); // 10 rounds, 3 consumed per shot

		assertTrue(weapon.consumeShot());
		assertEquals(7, weapon.getCurrentMagCapacity());

		assertTrue(weapon.consumeShot());
		assertEquals(4, weapon.getCurrentMagCapacity());

		assertTrue(weapon.consumeShot()); // 4 - 3 = 1, still > 0 so it succeeds
		assertEquals(1, weapon.getCurrentMagCapacity());

		// isMagazineEmpty() is `currentMagCapacity <= 0`, still false at 1, so one more consumeShot is attempted
		// and clamps at 0 rather than going negative.
		assertTrue(weapon.consumeShot());
		assertEquals(0, weapon.getCurrentMagCapacity());

		assertFalse(weapon.consumeShot(), "an empty magazine must refuse the shot");
	}

	@Test
	@DisplayName("Observation #13: Consumed_Amount == 0 gives a gun an infinite magazine")
	void gunWeapon_consumedAmountZero_infiniteMagazine() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(5, 0); // Consumed_Amount defaulted to 0 in the parser

		for (int i = 0; i < 100; i++) {
			assertTrue(weapon.consumeShot(), "shot " + i + " must still succeed — the magazine never depletes");
		}
		assertEquals(5, weapon.getCurrentMagCapacity(), "capacity is untouched after 100 'shots'");
	}

	@Test
	@DisplayName("IncendiaryWeapon.consumeShot subtracts Consume_Rate, clamped at 0 (Observation #17 corrected: the key is live)")
	void incendiaryWeapon_consumeShot_subtractsConsumeRate() {
		IncendiaryWeapon weapon = WeaponFixtures.incendiaryWeapon(10, 4);

		assertTrue(weapon.consumeShot());
		assertEquals(6, weapon.getCurrentMagCapacity());

		assertTrue(weapon.consumeShot());
		assertEquals(2, weapon.getCurrentMagCapacity());

		assertTrue(weapon.consumeShot()); // 2 - 4 clamps at 0, still succeeds because isMagazineEmpty() was false
		assertEquals(0, weapon.getCurrentMagCapacity());

		assertFalse(weapon.consumeShot());
	}

	@Test
	@DisplayName("base Weapon.consumeShot (melee/throwable/biological's inherited fallback) decrements by exactly 1")
	void baseConsumeShot_decrementsByOne_forMeleeThrowableBiological() {
		MeleeWeapon melee = WeaponFixtures.meleeWeapon(2);
		ThrowableWeapon throwable = WeaponFixtures.throwableWeapon(2);
		BiologicalWeapon biological = WeaponFixtures.biologicalWeapon(2);

		assertTrue(melee.consumeShot());
		assertEquals(1, melee.getCurrentMagCapacity());
		assertTrue(melee.consumeShot());
		assertEquals(0, melee.getCurrentMagCapacity());
		assertFalse(melee.consumeShot());

		assertTrue(throwable.consumeShot());
		assertEquals(1, throwable.getCurrentMagCapacity());

		assertTrue(biological.consumeShot());
		assertEquals(1, biological.getCurrentMagCapacity());
	}

	@Test
	@DisplayName("addAmmunition clamps at the ammunition's maxMagCapacity")
	void addAmmunition_clampsAtMax() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(10, 5);
		weapon.consumeShot(); // 10 -> 5

		weapon.addAmmunition(100); // way more than the 10-round max

		assertEquals(10, weapon.getCurrentMagCapacity());
	}

	@Test
	@DisplayName("isMagazineFull/isMagazineEmpty/requiresReload boundary conditions")
	void magazineBoundaries() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(4, 4);

		assertTrue(weapon.isMagazineFull());
		assertFalse(weapon.isMagazineEmpty());
		assertFalse(weapon.requiresReload(), "a full magazine never requires a reload");

		weapon.consumeShot(); // 4 -> 0

		assertFalse(weapon.isMagazineFull());
		assertTrue(weapon.isMagazineEmpty());
		assertTrue(weapon.requiresReload());
	}

	@Test
	@DisplayName("isMagazineFull is vacuously true and isMagazineEmpty is vacuously false for a weapon with no AmmunitionData")
	void noAmmunitionData_vacuousBoundaries() {
		// A weapon built with a null AmmunitionData (e.g. a melee weapon with no Ammunition: section at all).
		MeleeWeapon weapon = new MeleeWeapon(
				UUID.randomUUID(), "bare_knife", "&fBare Knife", WeaponType.MELEE, Material.IRON_HOE, 0, (short) 50,
				List.of(), false, null, new MeleeData(8.0, 3.0, 10, 0.5), null, null);

		assertTrue(weapon.isMagazineFull(), "no AmmunitionData -> isMagazineFull() vacuously true (Weapon.java:168)");
		assertFalse(weapon.isMagazineEmpty(), "no ReloadData -> isMagazineEmpty() vacuously false (Weapon.java:173)");
		assertFalse(weapon.requiresReload(), "no ReloadData -> requiresReload() is always false");
	}

}
