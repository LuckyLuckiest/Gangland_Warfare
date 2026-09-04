package org.luckyraven.gangland.turf.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves {@link CuboidRegion} construction-time normalisation and the inclusive {@code contains}/{@code overlaps}
 * semantics documented on the class: X/Z rectangle, Y ignored, both bound tests inclusive at every edge. Ties to
 * the "Pure-logic candidates" bullet in turf.md's Test Surface section.
 */
@DisplayName("CuboidRegion — normalisation, contains, overlaps")
class CuboidRegionTest {

	private static World worldNamed(String name) {
		World world = mock(World.class);
		when(world.getName()).thenReturn(name);
		return world;
	}

	@Test
	@DisplayName("constructor normalises corners regardless of input order")
	void constructor_normalisesCornerOrder() {
		CuboidRegion region = new CuboidRegion("world", 10, 20, -5, -15);

		assertTrue(region.getMinX() == -5 && region.getMaxX() == 10);
		assertTrue(region.getMinZ() == -15 && region.getMaxZ() == 20);
	}

	@Test
	@DisplayName("constructor normalises when corners are already in min/max order")
	void constructor_normalisesAlreadyOrderedCorners() {
		CuboidRegion region = new CuboidRegion("world", -5, -15, 10, 20);

		assertTrue(region.getMinX() == -5 && region.getMaxX() == 10);
		assertTrue(region.getMinZ() == -15 && region.getMaxZ() == 20);
	}

	@Test
	@DisplayName("contains is inclusive on every edge, Y is ignored entirely")
	void contains_isInclusiveOnEveryEdgeAndIgnoresY() {
		CuboidRegion region = new CuboidRegion("world", 0, 0, 10, 10);
		World        world  = worldNamed("world");

		assertTrue(region.contains(new Location(world, 0, 500, 0)), "min corner, sky-high Y");
		assertTrue(region.contains(new Location(world, 10, -60, 10)), "max corner, below bedrock Y");
		assertTrue(region.contains(new Location(world, 5, 64, 5)), "interior point");
	}

	@Test
	@DisplayName("contains rejects a point one block outside any edge")
	void contains_rejectsPointJustOutsideBounds() {
		CuboidRegion region = new CuboidRegion("world", 0, 0, 10, 10);
		World        world  = worldNamed("world");

		assertFalse(region.contains(new Location(world, -1, 64, 5)));
		assertFalse(region.contains(new Location(world, 11, 64, 5)));
		assertFalse(region.contains(new Location(world, 5, 64, -1)));
		assertFalse(region.contains(new Location(world, 5, 64, 11)));
	}

	@Test
	@DisplayName("contains rejects a location in a differently-named world at the same coordinates")
	void contains_rejectsDifferentWorldByName() {
		CuboidRegion region      = new CuboidRegion("world", 0, 0, 10, 10);
		World        otherWorld  = worldNamed("world_nether");

		assertFalse(region.contains(new Location(otherWorld, 5, 64, 5)));
	}

	@Test
	@DisplayName("contains rejects a null location and a location with a null world")
	void contains_rejectsNullLocationAndNullWorld() {
		CuboidRegion region = new CuboidRegion("world", 0, 0, 10, 10);

		assertFalse(region.contains(null));
		assertFalse(region.contains(new Location(null, 5, 64, 5)));
	}

	@Test
	@DisplayName("overlaps is true for a shared edge (one block of touching boundary)")
	void overlaps_trueOnSharedEdge() {
		CuboidRegion left  = new CuboidRegion("world", 0, 0, 10, 10);
		CuboidRegion right = new CuboidRegion("world", 10, 0, 20, 10);

		assertTrue(left.overlaps(right));
		assertTrue(right.overlaps(left), "overlap must be symmetric");
	}

	@Test
	@DisplayName("overlaps is true for a shared single corner")
	void overlaps_trueOnSharedCorner() {
		CuboidRegion bottomLeft = new CuboidRegion("world", 0, 0, 10, 10);
		CuboidRegion topRight   = new CuboidRegion("world", 10, 10, 20, 20);

		assertTrue(bottomLeft.overlaps(topRight));
	}

	@Test
	@DisplayName("overlaps is false for two disjoint regions separated by a gap")
	void overlaps_falseWhenDisjoint() {
		CuboidRegion left  = new CuboidRegion("world", 0, 0, 10, 10);
		CuboidRegion right = new CuboidRegion("world", 12, 0, 20, 10);

		assertFalse(left.overlaps(right));
	}

	@Test
	@DisplayName("overlaps is false for identical coordinates in a different world")
	void overlaps_falseAcrossDifferentWorlds() {
		CuboidRegion nether = new CuboidRegion("world_nether", 0, 0, 10, 10);
		CuboidRegion normal = new CuboidRegion("world", 0, 0, 10, 10);

		assertFalse(nether.overlaps(normal));
	}
}
