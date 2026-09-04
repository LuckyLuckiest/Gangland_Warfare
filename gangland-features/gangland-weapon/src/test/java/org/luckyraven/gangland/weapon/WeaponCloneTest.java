package org.luckyraven.gangland.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.dto.ModifiersData;
import org.luckyraven.gangland.weapon.dto.RecoilData;
import org.luckyraven.gangland.weapon.dto.ScopeData;
import org.luckyraven.gangland.weapon.dto.SpreadData;
import org.luckyraven.gangland.weapon.modifiers.action.FlatDamageModifier;
import org.luckyraven.gangland.weapon.support.WeaponFixtures;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link Weapon#clone()}/{@link Weapon#initClone(Weapon)}/{@code copyWithUUID} deep-copy semantics (weapons.md
 * W3 — Runtime weapon instance resolution; W4 — Giving a weapon).
 *
 * <p>{@code WeaponService.getWeapon} clones the registry template on every "mint a new instance" call
 * (Observation #6, weapons.md), so a clone that shares mutable DTO state with its template would corrupt every
 * other instance minted from the same template — e.g. one player's spread/recoil state leaking into another
 * player's copy of the same gun. This test proves every mutable DTO is genuinely deep-copied, not aliased.
 *
 * <p><b>New finding beyond the audit's numbered Observations table</b> — {@link #clone_bug_tagsMapIsSharedNotCopied()}:
 * {@code Weapon.tags} is a {@code final} field, so {@code Object.clone()} inside {@link Weapon#clone()} copies only
 * the reference, not the map. {@code initClone}'s {@code this.tags.clear()} therefore clears the one map both the
 * source and the clone point to — cloning a weapon that has already had {@code initializeTags}/{@code buildItem}
 * called on it silently wipes the source's tag cache too. Confirmed in code and pinned here; not previously listed
 * in weapons.md's Observations table.
 */
@DisplayName("Weapon.clone / initClone / copyWithUUID — deep copy semantics")
class WeaponCloneTest {

	@Test
	@DisplayName("copyWithUUID assigns a new UUID distinct from the source")
	void copyWithUUID_assignsNewUuid() {
		GunWeapon original = WeaponFixtures.gunWeapon(30, 1);
		UUID newUuid = UUID.randomUUID();

		GunWeapon copy = original.copyWithUUID(newUuid);

		assertEquals(newUuid, copy.getUuid());
		assertNotSame(original.getUuid(), copy.getUuid());
	}

	@Test
	@DisplayName("clone deep-copies ModifiersData — mutating the clone's list must not affect the source")
	void clone_deepCopiesModifiersData() {
		GunWeapon original = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.setFlatDamage(new FlatDamageModifier(1.0));
		original.setModifiersData(modifiers);

		GunWeapon copy = original.clone();

		assertNotSame(original.getModifiersData(), copy.getModifiersData());
		copy.getModifiersData().addBreakBlock(null); // mutate the clone's list

		assertTrue(original.getModifiersData().getBreakBlocks().isEmpty(),
		           "mutating the clone's breakBlocks list must not affect the source's list");
	}

	@Test
	@DisplayName("clone deep-copies RecoilData's pattern list (each String[] entry is independently cloned)")
	void clone_deepCopiesRecoilPattern() {
		GunWeapon original = WeaponFixtures.gunWeapon(30, 1);
		RecoilData recoil = new RecoilData();
		recoil.setAmount(1.0);
		List<String[]> pattern = new java.util.ArrayList<>();
		pattern.add(new String[]{"1.0", "2.0"});
		recoil.setPattern(pattern);
		original.setRecoilData(recoil);

		GunWeapon copy = original.clone();
		copy.getRecoilData().getPattern().get(0)[0] = "999.0";

		assertEquals("1.0", original.getRecoilData().getPattern().get(0)[0],
		             "the clone's pattern arrays must be independent copies, not shared references");
	}

	@Test
	@DisplayName("clone resets scoped to false even when the source was actively scoped")
	void clone_resetsScopedState() {
		GunWeapon original = WeaponFixtures.gunWeapon(30, 1);
		ScopeData scope = new ScopeData();
		scope.setLevel(2);
		scope.setScoped(true);
		original.setScopeData(scope);

		GunWeapon copy = original.clone();

		assertTrue(original.getScopeData().isScoped(), "the source's scope state is untouched");
		assertFalse(copy.getScopeData().isScoped(), "a fresh clone must never start pre-scoped");
	}

	@Test
	@DisplayName("clone gives independent RecoilManager/SpreadManager/DurabilityCalculator instances")
	void clone_freshManagerInstances() {
		GunWeapon original = WeaponFixtures.gunWeapon(30, 1);

		GunWeapon copy = original.clone();

		assertNotSame(original.getRecoil(), copy.getRecoil());
		assertNotSame(original.getSpread(), copy.getSpread());
		assertNotSame(original.getDurabilityCalculator(), copy.getDurabilityCalculator());
	}

	@Test
	@DisplayName("clone resets currentMagCapacity to the ammunition's max, independent of the source's current state")
	void clone_resetsMagCapacityToMax() {
		GunWeapon original = WeaponFixtures.gunWeapon(10, 3);
		original.consumeShot(); // drop to 7/10

		GunWeapon copy = original.clone();

		assertEquals(7, original.getCurrentMagCapacity(), "the source's magazine is untouched by cloning");
		assertEquals(10, copy.getCurrentMagCapacity(), "a clone starts with a full magazine again");
	}

	@Test
	@DisplayName("clone deep-copies GunWeapon's own DamageData (subclass-level clone, not just Weapon's)")
	void clone_gunWeaponSubclass_deepCopiesDamageData() {
		GunWeapon original = WeaponFixtures.gunWeapon(30, 1);
		original.getDamageData().setHeadDamage(5.0);

		GunWeapon copy = original.clone();
		copy.getDamageData().setHeadDamage(999.0);

		assertNotSame(original.getDamageData(), copy.getDamageData());
		assertEquals(5.0, original.getDamageData().getHeadDamage(),
		             "mutating the clone's DamageData must not affect the source's");
	}

	@Test
	@DisplayName("BUG: cloning a weapon whose tags were already populated silently wipes the source's tags too")
	void clone_bug_tagsMapIsSharedNotCopied() {
		GunWeapon original = WeaponFixtures.gunWeapon(30, 1);
		original.initializeTags(mock(org.luckyraven.keystone.item.ItemBuilder.class));

		assertFalse(original.getTags().isEmpty(), "sanity check: tags were actually populated before cloning");

		original.clone();

		// `tags` is `final`, so Object.clone() copied only the reference — initClone()'s `this.tags.clear()` on
		// the freshly-cloned copy cleared the exact same TreeMap the source still points to.
		assertTrue(original.getTags().isEmpty(),
		           "cloning must not mutate the source, but the source's tag cache is now empty");
	}

	@Test
	@DisplayName("a weapon with no ModifiersData/RecoilData/ScopeData/SpreadData configured clones cleanly to all-null")
	void clone_allNullOptionalData_staysNull() {
		GunWeapon original = WeaponFixtures.gunWeapon(30, 1);
		// WeaponFixtures never sets ModifiersData/RecoilData/ScopeData/SpreadData/SoundData/DurabilityData/
		// ReloadActionBarData — matches an admin weapon YAML missing every optional section.

		GunWeapon copy = original.clone();

		assertSame(null, copy.getModifiersData());
		assertSame(null, copy.getRecoilData());
		assertSame(null, copy.getScopeData());
		assertSame(null, copy.getSpreadData());
	}

}
