package org.luckyraven.gangland.weapon.raytrace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the rocket AOE maths of {@link SteppedProjectileTask} after the WP-01 fix (P0, weapons.md observation #1).
 *
 * <p>Before 0.8.3 {@code WeaponShooting} passed {@code DamageData.explosionDamage} in as the blast <i>radius</i> and
 * {@code fireExplosion} then dealt a hard-coded {@code 20} at the centre. With {@code rocket_launcher.yml}'s
 * {@code Explosion_Damage: 50} that produced a 50-block-radius blast that swept a 100-block cube of
 * {@code getNearbyEntities} on every shot. Radius and damage are now two independent config values, and this class
 * pins the falloff curve that consumes them.
 */
@DisplayName("SteppedProjectileTask.falloffDamage — explosion radius and damage are independent (WP-01)")
class SteppedProjectileTaskTest {

	@Test
	@DisplayName("full configured damage lands at the blast centre")
	void falloffDamage_atCentre_isFullConfiguredDamage() {
		assertEquals(50.0, SteppedProjectileTask.falloffDamage(50.0, 4.0, 0.0), 1e-9);
	}

	@Test
	@DisplayName("damage tapers linearly across the radius, not across the damage value")
	void falloffDamage_taperIsScaledByRadius() {
		// Half-way through a 4-block blast: half the configured damage. The pre-fix code divided by the damage
		// value (50), so a target 2 blocks out took 20 * (1 - 2/50) = 19.2 instead.
		assertEquals(25.0, SteppedProjectileTask.falloffDamage(50.0, 4.0, 2.0), 1e-9);
		assertEquals(12.5, SteppedProjectileTask.falloffDamage(50.0, 4.0, 3.0), 1e-9);
	}

	@Test
	@DisplayName("a target beyond the radius takes nothing — a 50-damage rocket no longer reaches 49 blocks out")
	void falloffDamage_outsideRadius_isZero() {
		assertEquals(0.0, SteppedProjectileTask.falloffDamage(50.0, 4.0, 4.0), 1e-9);
		assertEquals(0.0, SteppedProjectileTask.falloffDamage(50.0, 4.0, 49.0), 1e-9);
	}

	@Test
	@DisplayName("a weapon that configures no blast deals nothing")
	void falloffDamage_zeroRadiusOrDamage_isZero() {
		assertEquals(0.0, SteppedProjectileTask.falloffDamage(50.0, 0.0, 0.0), 1e-9);
		assertEquals(0.0, SteppedProjectileTask.falloffDamage(0.0, 4.0, 0.0), 1e-9);
	}

}
