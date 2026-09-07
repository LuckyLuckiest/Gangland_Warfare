package org.luckyraven.gangland.weapon.item;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * {@link WeaponItemPredicates#WEAPON} and {@link WeaponItemPredicates#AMMUNITION} — moved out of the core's
 * {@code ItemPredicates} when the weapon module was split out (flip 4, T14/T20). Both route through
 * {@code ItemBuilder.hasNBTTag}, which degrades to {@link RecordingNbtAccessor}'s in-memory map with no NBT provider
 * or live server required (documentation/TESTING.md §4).
 */
@DisplayName("WeaponItemPredicates")
class WeaponItemPredicatesTest {

	private RecordingNbtAccessor accessor;

	@BeforeEach
	void setUp() {
		NbtBridge.install(accessor = new RecordingNbtAccessor());
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("WEAPON and AMMUNITION are false for a null stack")
	void nullStack_isNeitherWeaponNorAmmunition() {
		assertFalse(WeaponItemPredicates.WEAPON.test(null));
		assertFalse(WeaponItemPredicates.AMMUNITION.test(null));
	}

	@Test
	@DisplayName("WEAPON and AMMUNITION are false for a stack carrying neither NBT tag")
	void taglessStack_isNeitherWeaponNorAmmunition() {
		ItemStack stack = mock(ItemStack.class);

		assertFalse(WeaponItemPredicates.WEAPON.test(stack));
		assertFalse(WeaponItemPredicates.AMMUNITION.test(stack));
	}
}
