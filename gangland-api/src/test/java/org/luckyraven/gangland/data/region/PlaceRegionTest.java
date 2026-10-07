package org.luckyraven.gangland.data.region;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PlaceRegion}: the world-name check, null safety and the defensive tag copy (CONTRACTS C1).
 */
@DisplayName("PlaceRegion - one named place")
class PlaceRegionTest {

	private static Location at(String world, double x, double y, double z) {
		World w = mock(World.class);
		when(w.getName()).thenReturn(world);
		Location location = mock(Location.class);
		when(location.getWorld()).thenReturn(w);
		when(location.getX()).thenReturn(x);
		when(location.getY()).thenReturn(y);
		when(location.getZ()).thenReturn(z);
		return location;
	}

	private static PlaceRegion region(Set<String> tags) {
		return new PlaceRegion("test:1", "Docks", "world", RegionShape.Cuboid.column(0, 0, 9, 9),
		                       PlaceRegion.NO_OWNER, tags);
	}

	@Test
	@DisplayName("contains is true inside the shape in the same world and false in another world")
	void contains_checksTheWorldName() {
		PlaceRegion region = region(Set.of());

		assertTrue(region.contains(at("world", 5, 64, 5)));
		assertFalse(region.contains(at("nether", 5, 64, 5)));
		assertFalse(region.contains(at("world", 50, 64, 5)));
	}

	@Test
	@DisplayName("a null location, or one with no world, is never inside")
	void contains_nullIsFalse() {
		PlaceRegion region  = region(Set.of());
		Location    noWorld = mock(Location.class);

		assertFalse(region.contains(null));
		assertFalse(region.contains(noWorld));
	}

	@Test
	@DisplayName("the tag set is copied, so later edits of the caller set do not leak in")
	void tags_areCopied() {
		Set<String> tags   = new HashSet<>(Set.of(PlaceRegion.TAG_HIDEOUT));
		PlaceRegion region = region(tags);

		tags.add(PlaceRegion.TAG_DISTRICT);

		assertTrue(region.hasTag(PlaceRegion.TAG_HIDEOUT));
		assertFalse(region.hasTag(PlaceRegion.TAG_DISTRICT));
	}

	@Test
	@DisplayName("a null name becomes empty, null tags become none, a null id is rejected")
	void nullsAreNormalised() {
		PlaceRegion region = new PlaceRegion("a:1", null, "world", RegionShape.Cuboid.column(0, 0, 1, 1), 3, null);

		assertEquals("", region.name());
		assertTrue(region.tags().isEmpty());
		assertThrows(NullPointerException.class,
		             () -> new PlaceRegion(null, "x", "world", RegionShape.Cuboid.column(0, 0, 1, 1), 3, null));
	}
}
