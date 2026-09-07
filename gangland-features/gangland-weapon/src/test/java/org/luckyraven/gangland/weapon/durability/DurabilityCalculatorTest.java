package org.luckyraven.gangland.weapon.durability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.support.WeaponFixtures;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;
import org.luckyraven.keystone.item.ItemBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link DurabilityCalculator}'s weapon-durability ⇄ item-damage-bar conversion (weapons.md W16 — Durability
 * loss and breakage).
 *
 * <p>{@link org.luckyraven.keystone.item.ItemBuilder} is mocked rather than backed by a real {@code ItemStack}:
 * {@code getItemDamagedDurability()}/{@code setDurability()} route through Bukkit's {@code ItemMeta}, which needs a
 * live {@code Bukkit.getItemFactory()} this test suite does not stand up. Mocking the collaborator keeps the test on
 * the pure arithmetic in {@code DurabilityCalculator} itself, which is what this class actually owns.
 *
 * <p>Pins Observation #33 (weapons.md): a weapon YAML with {@code Durability.Base: 0} divides by zero. The test
 * corrects the audit's "undefined {@code (short)} cast" framing — the JLS defines double→int narrowing of NaN/
 * Infinity precisely (NaN and both infinities all narrow to {@code 0} once the pre-cast arithmetic is
 * {@code 0 * Infinity} or {@code finite / 0} feeding a floor()), so the observed outcome is a silently-wrong
 * {@code 0} damage value, not a crash or garbage short.
 */
@DisplayName("DurabilityCalculator — weapon durability <-> item damage-bar conversion")
class DurabilityCalculatorTest {

	@Test
	@DisplayName("getWeaponDurability — full health weapon reports zero item damage")
	void getWeaponDurability_fullHealth_zeroDamage() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1, (short) 100);
		DurabilityCalculator calculator = weapon.getDurabilityCalculator();

		ItemBuilder builder = mock(ItemBuilder.class);
		when(builder.getItemMaxDurability()).thenReturn((short) 250);

		assertEquals((short) 0, calculator.getWeaponDurability(builder));
	}

	@Test
	@DisplayName("getWeaponDurability — half-damaged weapon scales onto the item's own max durability")
	void getWeaponDurability_scalesOntoItemMax() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1, (short) 100);
		weapon.setCurrentDurability((short) 50); // half the weapon's own 100-point durability lost
		DurabilityCalculator calculator = weapon.getDurabilityCalculator();

		ItemBuilder builder = mock(ItemBuilder.class);
		when(builder.getItemMaxDurability()).thenReturn((short) 250); // e.g. an iron tool's vanilla max

		// scale = 250/100 = 2.5; lost = 50; floor(50 * 2.5) = 125
		assertEquals((short) 125, calculator.getWeaponDurability(builder));
	}

	@Test
	@DisplayName("calculateWeaponDurabilityFromItem round-trips through getWeaponDurability")
	void calculateWeaponDurabilityFromItem_roundTrips() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1, (short) 100);
		weapon.setCurrentDurability((short) 40);
		DurabilityCalculator calculator = weapon.getDurabilityCalculator();

		ItemBuilder builder = mock(ItemBuilder.class);
		when(builder.getItemMaxDurability()).thenReturn((short) 250);

		short itemDamage = calculator.getWeaponDurability(builder);
		when(builder.getItemDamagedDurability()).thenReturn(itemDamage);

		assertEquals((short) 40, calculator.calculateWeaponDurabilityFromItem(builder),
		             "reading the item's damage bar back must reconstruct the weapon's current durability");
	}

	@Test
	@DisplayName("calculateWeaponDurabilityFromItem — material with no vanilla durability bar reads as undamaged")
	void calculateWeaponDurabilityFromItem_itemMaxZero_treatedAsUndamaged() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1, (short) 100);
		weapon.setCurrentDurability((short) 10); // would be heavily damaged if the item could show it
		DurabilityCalculator calculator = weapon.getDurabilityCalculator();

		ItemBuilder builder = mock(ItemBuilder.class);
		when(builder.getItemMaxDurability()).thenReturn((short) 0); // e.g. a non-damageable material

		assertEquals((short) 100, calculator.calculateWeaponDurabilityFromItem(builder),
		             "a material with no damage bar cannot encode weapon durability, so it is treated as full");
	}

	@Test
	@DisplayName("getWeaponDurability — Durability.Base: 0 divides by zero and silently yields 0, not a thrown exception (Observation #33, weapons.md)")
	void getWeaponDurability_zeroWeaponMax_divisionByZero_yieldsZeroNotCrash() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1, (short) 0);
		// currentDurability defaults to durability (0); force it away from the max to exercise the
		// weaponDurabilityLost != 0 branch too.
		weapon.setCurrentDurability((short) 5);
		DurabilityCalculator calculator = weapon.getDurabilityCalculator();

		ItemBuilder builder = mock(ItemBuilder.class);
		when(builder.getItemMaxDurability()).thenReturn((short) 250);

		// scale = 250/0 = Infinity; lost = 0-5 = -5; floor(-5 * Infinity) = -Infinity; (short)(int) -Infinity == 0
		// per JLS 5.1.3 (double->int narrowing of an infinite value saturates to Integer.MIN_VALUE, whose low 16
		// bits are 0 once narrowed again to short) — no exception is thrown.
		assertEquals((short) 0, calculator.getWeaponDurability(builder));
	}

}
