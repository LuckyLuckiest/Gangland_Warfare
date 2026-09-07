package org.luckyraven.gangland.weapon.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Pins that {@link DamageData} carries the blast radius and the blast damage as two separate values (WP-01, P0,
 * weapons.md observation #1). Before 0.8.3 only {@code explosionDamage} existed and the shooting path reused it as
 * the radius, so {@code Explosion_Damage: 50} meant "50 blocks" as well as "50 damage".
 */
@DisplayName("DamageData — explosion radius is independent of explosion damage (WP-01)")
class DamageDataTest {

	@Test
	@DisplayName("radius and damage are stored and read back independently")
	void explosionRadiusAndDamage_areSeparateValues() {
		DamageData data = new DamageData();
		data.setExplosionDamage(50.0);
		data.setExplosionRadius(4.0);

		assertEquals(50.0, data.getExplosionDamage(), 1e-9);
		assertEquals(4.0, data.getExplosionRadius(), 1e-9);
	}

	@Test
	@DisplayName("clone carries both values onto the copy")
	void clone_copiesRadiusAndDamage() {
		DamageData data = new DamageData();
		data.setExplosionDamage(50.0);
		data.setExplosionRadius(4.0);

		DamageData copy = data.clone();

		assertNotSame(data, copy);
		assertEquals(50.0, copy.getExplosionDamage(), 1e-9);
		assertEquals(4.0, copy.getExplosionRadius(), 1e-9);
	}

}
