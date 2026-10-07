package org.luckyraven.gangland.data.region;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two {@link RegionShape} kinds: corner normalisation, inclusive edges, Y handling and footprints (CONTRACTS C1).
 */
@DisplayName("RegionShape - cuboid and sphere")
class RegionShapeTest {

	@Test
	@DisplayName("Cuboid.of normalises two corners given in any order")
	void cuboid_normalisesCorners() {
		RegionShape.Cuboid cuboid = RegionShape.Cuboid.of(10, 70, 20, -5, 60, 0);

		assertEquals(new RegionShape.Cuboid(-5, 60, 0, 10, 70, 20), cuboid);
	}

	@Test
	@DisplayName("a cuboid includes both of its edge blocks and excludes the next block out")
	void cuboid_edgesAreInclusive() {
		RegionShape.Cuboid cuboid = RegionShape.Cuboid.of(0, 0, 0, 4, 4, 4);

		assertTrue(cuboid.contains(0, 0, 0));
		assertTrue(cuboid.contains(4.99, 4.99, 4.99), "coordinates are floored, so 4.99 is still block 4");
		assertFalse(cuboid.contains(5.0, 2, 2));
		assertFalse(cuboid.contains(-0.01, 2, 2), "-0.01 floors to block -1");
	}

	@Test
	@DisplayName("a column ignores Y completely")
	void column_ignoresY() {
		RegionShape.Cuboid column = RegionShape.Cuboid.column(0, 0, 9, 9);

		assertTrue(column.contains(5, -64, 5));
		assertTrue(column.contains(5, 319, 5));
		assertFalse(column.contains(10, 64, 5));
	}

	@Test
	@DisplayName("a sphere contains its surface and centre and nothing beyond the radius")
	void sphere_contains() {
		RegionShape.Sphere sphere = new RegionShape.Sphere(0, 64, 0, 5);

		assertTrue(sphere.contains(0, 64, 0));
		assertTrue(sphere.contains(5, 64, 0), "exactly on the radius is inside");
		assertFalse(sphere.contains(4, 64, 4), "distance 5.66");
		assertFalse(sphere.contains(0, 70, 0));
	}

	@Test
	@DisplayName("footprints: a cuboid counts blocks, a sphere is pi r squared")
	void footprints() {
		assertEquals(25.0, RegionShape.Cuboid.of(0, 0, 0, 4, 9, 4).footprint());
		assertEquals(100.0, RegionShape.Cuboid.column(0, 0, 9, 9).footprint());
		assertEquals(Math.PI * 4 * 4, new RegionShape.Sphere(0, 0, 0, 4).footprint(), 1e-9);
	}
}
