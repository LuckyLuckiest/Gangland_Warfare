package org.luckyraven.gangland.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.projectile.ProjectileType;
import org.luckyraven.gangland.weapon.reload.ReloadType;
import org.luckyraven.gangland.weapon.types.WeaponType;
import org.luckyraven.gangland.weapon.types.throwable.ThrowableType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers the four config-string parsing enums the weapons.md audit's Test Surface groups together under one
 * bullet ("WeaponType.getType, ProjectileType.getType, ThrowableType.getType, ReloadType.getType"):
 * {@link WeaponType}, {@link ProjectileType}, {@link ThrowableType} and {@link ReloadType}. Each is a tiny,
 * structurally identical {@code getType(String)} switch, so one test class covers the family instead of four
 * near-empty ones.
 *
 * <p>Pins Observation #30 (weapons.md): an unrecognised {@code Category} silently falls through to
 * {@link WeaponType#OTHER} rather than failing loudly.
 *
 * <p>Pins Observation #16 (weapons.md): {@link ReloadType#amount} is a mutable field on the shared enum
 * constant, not per-weapon state — two weapons parsed with different {@code num-N} amounts collide on whichever
 * loaded last.
 */
@DisplayName("Type-parsing enums — WeaponType / ProjectileType / ThrowableType / ReloadType")
class TypeEnumParsingTest {

	@Test
	@DisplayName("WeaponType.getType maps every known category alias, case-insensitively")
	void weaponType_mapsKnownAliases() {
		assertSame(WeaponType.GUN, WeaponType.getType("gun"));
		assertSame(WeaponType.MELEE, WeaponType.getType("MELEE"));
		assertSame(WeaponType.THROWABLE, WeaponType.getType("throwable"));
		assertSame(WeaponType.THROWABLE, WeaponType.getType("throw"));
		assertSame(WeaponType.THROWABLE, WeaponType.getType("grenade"));
		assertSame(WeaponType.THROWABLE, WeaponType.getType("projectile"));
		assertSame(WeaponType.THROWABLE, WeaponType.getType("proj"));
		assertSame(WeaponType.INCENDIARY, WeaponType.getType("incendiary"));
		assertSame(WeaponType.INCENDIARY, WeaponType.getType("fire"));
		assertSame(WeaponType.BIOLOGICAL, WeaponType.getType("biological"));
		assertSame(WeaponType.BIOLOGICAL, WeaponType.getType("biology"));
		assertSame(WeaponType.BIOLOGICAL, WeaponType.getType("bio"));
	}

	@Test
	@DisplayName("WeaponType.getType — an unrecognised Category silently becomes OTHER (Observation #30, weapons.md)")
	void weaponType_unknownCategory_becomesOther() {
		assertSame(WeaponType.OTHER, WeaponType.getType("not-a-real-category"));
		assertSame(WeaponType.OTHER, WeaponType.getType(""));
	}

	@Test
	@DisplayName("ProjectileType.getType maps flare/spread/rocket, defaults to BULLET")
	void projectileType_mapsKnownAliases() {
		assertSame(ProjectileType.FLARE, ProjectileType.getType("flare"));
		assertSame(ProjectileType.SPREAD, ProjectileType.getType("spread"));
		assertSame(ProjectileType.ROCKET, ProjectileType.getType("rocket"));
		assertSame(ProjectileType.BULLET, ProjectileType.getType("bullet"));
		assertSame(ProjectileType.BULLET, ProjectileType.getType("anything-else"));
	}

	@Test
	@DisplayName("ThrowableType.getType maps smoke/stun, defaults to EXPLOSIVE including for a null Type: key")
	void throwableType_mapsKnownAliasesAndNullDefault() {
		assertSame(ThrowableType.SMOKE, ThrowableType.getType("smoke"));
		assertSame(ThrowableType.STUN, ThrowableType.getType("stun"));
		assertSame(ThrowableType.EXPLOSIVE, ThrowableType.getType("explosive"));
		assertSame(ThrowableType.EXPLOSIVE, ThrowableType.getType(null),
		           "missing Type: key must keep the legacy EXPLOSIVE behaviour");
	}

	@Test
	@DisplayName("ReloadType.getType maps one/num, defaults to INSTANT")
	void reloadType_mapsKnownAliases() {
		assertSame(ReloadType.ONE, ReloadType.getType("one"));
		assertSame(ReloadType.NUM, ReloadType.getType("num"));
		assertSame(ReloadType.INSTANT, ReloadType.getType("instant"));
		assertSame(ReloadType.INSTANT, ReloadType.getType("anything-else"));
	}

	@Test
	@DisplayName("ReloadType.amount is a shared mutable field on the enum constant (Observation #16, weapons.md)")
	void reloadType_amountIsSharedMutableState() {
		// Simulates AmmunitionSectionParser.parse: `reloadType.setAmount(typeAmount)` for two different weapon
		// files that both use "num-N" reloads with different N.
		ReloadType.NUM.setAmount(3);
		assertEquals(3, ReloadType.NUM.getAmount());

		ReloadType.NUM.setAmount(7);

		// The first weapon's amount is gone — every holder of ReloadType.NUM (including the first weapon's
		// already-constructed Reload instance, which read this field at construction time) now shares 7.
		assertEquals(7, ReloadType.NUM.getAmount(), "the enum constant has exactly one 'amount' — "
		                                            + "later parses silently overwrite earlier ones");
	}

}
