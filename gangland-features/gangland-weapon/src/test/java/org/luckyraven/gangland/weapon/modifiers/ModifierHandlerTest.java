package org.luckyraven.gangland.weapon.modifiers;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.weapon.dto.ModifiersData;
import org.luckyraven.gangland.weapon.modifiers.action.ArmorPiercingModifier;
import org.luckyraven.gangland.weapon.modifiers.action.FlatDamageModifier;
import org.luckyraven.gangland.weapon.modifiers.action.PenetrationModifier;
import org.luckyraven.gangland.weapon.projectile.ProjectileState;
import org.luckyraven.gangland.weapon.support.WeaponFixtures;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure-math coverage for {@link ModifierHandler} (weapons.md W17 — Modifiers: penetration, ricochet, block break,
 * tracer, AP, flat damage). No live Bukkit server is needed: {@code LivingEntity}/{@code AttributeInstance}/
 * {@code Block} are wide Bukkit interfaces mocked with Mockito per {@code TESTING.md} §6, and {@code Material}/
 * {@code Attribute} are plain enums resolvable with only the API jar on the classpath.
 */
@DisplayName("ModifierHandler — armor piercing, flat damage, penetration gating")
class ModifierHandlerTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	@Test
	@DisplayName("calculateArmorPiercingDamage returns baseDamage unchanged when no ArmorPiercing modifier is configured")
	void calculateArmorPiercingDamage_noModifier_returnsBaseDamage() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		weapon.setModifiersData(new ModifiersData());
		LivingEntity target = mock(LivingEntity.class);

		double result = ModifierHandler.calculateArmorPiercingDamage(20.0, target, weapon);

		assertEquals(20.0, result);
	}

	@Test
	@DisplayName("calculateArmorPiercingDamage pre-compensates for Minecraft's own armor reduction")
	void calculateArmorPiercingDamage_withModifier_preCompensates() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.setArmorPiercing(new ArmorPiercingModifier(0.5)); // bypasses half the target's armor
		weapon.setModifiersData(modifiers);

		LivingEntity target = mock(LivingEntity.class);
		AttributeInstance armorInstance = mock(AttributeInstance.class);
		when(armorInstance.getValue()).thenReturn(20.0); // capped armor value
		when(target.getAttribute(org.mockito.ArgumentMatchers.any(Attribute.class))).thenReturn(armorInstance);

		// normalReduction = min(20,20)/25 = 0.8; effectiveArmor = 20*(1-0.5) = 10; piercingReduction = 10/25 = 0.4
		// normalDamage = 20*(1-0.8) = 4; piercingDamage = 20*(1-0.4) = 12; result = 20 + (12-4) = 28
		double result = ModifierHandler.calculateArmorPiercingDamage(20.0, target, weapon);

		assertEquals(28.0, result, 0.0001);
	}

	@Test
	@DisplayName("calculateArmorPiercingDamage returns baseDamage when the target has no armor attribute instance")
	void calculateArmorPiercingDamage_noAttributeInstance_returnsBaseDamage() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.setArmorPiercing(new ArmorPiercingModifier(0.5));
		weapon.setModifiersData(modifiers);

		LivingEntity target = mock(LivingEntity.class); // getAttribute(...) defaults to null
		double result = ModifierHandler.calculateArmorPiercingDamage(20.0, target, weapon);

		assertEquals(20.0, result);
	}

	@Test
	@DisplayName("applyFlatDamage adds the configured bonus, or leaves damage unchanged when absent")
	void applyFlatDamage_addsBonusOrPassesThrough() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);

		ModifiersData withoutFlat = new ModifiersData();
		weapon.setModifiersData(withoutFlat);
		assertEquals(10.0, ModifierHandler.applyFlatDamage(10.0, weapon));

		ModifiersData withFlat = new ModifiersData();
		withFlat.setFlatDamage(new FlatDamageModifier(2.5));
		weapon.setModifiersData(withFlat);
		assertEquals(12.5, ModifierHandler.applyFlatDamage(10.0, weapon));
	}

	@Test
	@DisplayName("handleEntityPenetration increments the counter, applies the reduction, and reports whether budget remains")
	void handleEntityPenetration_incrementsAndReports() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.setPenetration(new PenetrationModifier(0, 2, 0.25));
		weapon.setModifiersData(modifiers);

		ProjectileState state = new ProjectileState(weapon, 100.0);

		assertTrue(ModifierHandler.handleEntityPenetration(state));
		assertEquals(1, state.getEntitiesPenetrated());
		assertEquals(75.0, state.getCurrentDamage(), 0.0001);

		// Second penetration reaches the entity budget (2) and there is no block budget (0), so the ray must stop.
		assertFalse(ModifierHandler.handleEntityPenetration(state));
		assertEquals(2, state.getEntitiesPenetrated());
	}

	@Test
	@DisplayName("handleEntityPenetration returns false immediately once canPenetrateEntity is already exhausted")
	void handleEntityPenetration_alreadyExhausted_returnsFalseWithoutMutating() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.setPenetration(new PenetrationModifier(0, 1, 0.25));
		weapon.setModifiersData(modifiers);

		ProjectileState state = new ProjectileState(weapon, 100.0);
		state.setEntitiesPenetrated(1); // already at budget

		assertFalse(ModifierHandler.handleEntityPenetration(state));
		assertEquals(1, state.getEntitiesPenetrated(), "an exhausted budget must not increment further");
	}

	@Test
	@DisplayName("handleBlockPenetration refuses non-penetrable materials even with budget remaining")
	void handleBlockPenetration_nonPenetrableMaterial_refuses() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.setPenetration(new PenetrationModifier(3, 0, 0.1));
		weapon.setModifiersData(modifiers);

		ProjectileState state = new ProjectileState(weapon, 100.0);
		Block solidStone = mock(Block.class);
		when(solidStone.getType()).thenReturn(Material.STONE);

		assertFalse(ModifierHandler.handleBlockPenetration(state, solidStone));
		assertEquals(0, state.getBlocksPenetrated());
	}

	@Test
	@DisplayName("handleBlockPenetration accepts glass/pane/leaves/fence-style thin blocks and applies the reduction")
	void handleBlockPenetration_penetrableMaterial_accepts() {
		GunWeapon weapon = WeaponFixtures.gunWeapon(30, 1);
		ModifiersData modifiers = new ModifiersData();
		modifiers.setPenetration(new PenetrationModifier(3, 0, 0.2));
		weapon.setModifiersData(modifiers);

		ProjectileState state = new ProjectileState(weapon, 100.0);
		Block glass = mock(Block.class);
		when(glass.getType()).thenReturn(Material.GLASS);

		assertTrue(ModifierHandler.handleBlockPenetration(state, glass));
		assertEquals(1, state.getBlocksPenetrated());
		assertEquals(80.0, state.getCurrentDamage(), 0.0001);
	}

}
