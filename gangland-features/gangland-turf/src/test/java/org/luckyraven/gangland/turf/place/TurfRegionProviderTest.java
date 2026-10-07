package org.luckyraven.gangland.turf.place;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.data.region.RegionShape;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurfRegionProviderTest {

	private World world;
	private TurfManager turfManager;
	private TurfRegionProvider provider;

	@BeforeEach
	void setUp() {
		world = mock(World.class);
		turfManager = mock(TurfManager.class);
		when(world.getName()).thenReturn("world");
		provider = new TurfRegionProvider(turfManager);
	}

	@Test
	void unclaimedTurf_isATurfWithNoOwner() {
		Turf turf = new Turf(1, "Unclaimed", new CuboidRegion("world", 0, 0, 10, 10), null,
		                      BigDecimal.ZERO, System.currentTimeMillis(), 0);
		Location at = new Location(world, 5, 64, 5);

		when(turfManager.findAt(at)).thenReturn(turf);

		List<PlaceRegion> regions = provider.regionsAt(at);

		assertEquals(1, regions.size());
		PlaceRegion region = regions.get(0);
		assertEquals("turf:1", region.id());
		assertEquals("Unclaimed", region.name());
		assertEquals("world", region.world());
		assertEquals(PlaceRegion.NO_OWNER, region.ownerGangId());
		assertTrue(region.hasTag(PlaceRegion.TAG_TURF));
		assertFalse(region.hasTag(PlaceRegion.TAG_HIDEOUT));
	}

	@Test
	void ownedTurf_isAHideoutOwnedByTheGang() {
		Turf turf = new Turf(2, "Owned", new CuboidRegion("world", 20, 20, 30, 30), 5,
		                      BigDecimal.TEN, System.currentTimeMillis(), 0);
		Location at = new Location(world, 25, 64, 25);

		when(turfManager.findAt(at)).thenReturn(turf);

		List<PlaceRegion> regions = provider.regionsAt(at);

		assertEquals(1, regions.size());
		PlaceRegion region = regions.get(0);
		assertEquals("turf:2", region.id());
		assertEquals("Owned", region.name());
		assertEquals("world", region.world());
		assertEquals(5, region.ownerGangId());
		assertTrue(region.hasTag(PlaceRegion.TAG_TURF));
		assertTrue(region.hasTag(PlaceRegion.TAG_HIDEOUT));
	}

	@Test
	void outsideEveryTurf_isEmpty() {
		Location at = new Location(world, 100, 64, 100);

		when(turfManager.findAt(at)).thenReturn(null);

		List<PlaceRegion> regions = provider.regionsAt(at);

		assertTrue(regions.isEmpty());
	}

	@Test
	void yIsIgnored() {
		Turf turf = new Turf(3, "YIgnored", new CuboidRegion("world", 0, 0, 10, 10), null,
		                      BigDecimal.ZERO, System.currentTimeMillis(), 0);
		Location at = new Location(world, 5, 64, 5);

		when(turfManager.findAt(at)).thenReturn(turf);

		List<PlaceRegion> regions = provider.regionsAt(at);

		assertEquals(1, regions.size());
		PlaceRegion region = regions.get(0);

		// The shape must be a column: unbounded in Y
		assertTrue(region.shape() instanceof RegionShape.Cuboid);
		RegionShape.Cuboid cuboid = (RegionShape.Cuboid) region.shape();
		assertEquals(Integer.MIN_VALUE, cuboid.minY());
		assertEquals(Integer.MAX_VALUE, cuboid.maxY());

		// X and Z are bounded by the turf region
		assertEquals(0, cuboid.minX());
		assertEquals(10, cuboid.maxX());
		assertEquals(0, cuboid.minZ());
		assertEquals(10, cuboid.maxZ());

		// Verify column containment: same X/Z but different Y
		assertTrue(cuboid.contains(5, 0, 5), "Should contain Y=0");
		assertTrue(cuboid.contains(5, 64, 5), "Should contain Y=64");
		assertTrue(cuboid.contains(5, 255, 5), "Should contain Y=255");
	}

	@Test
	void placeNamesLocate_returnsDisplayName() {
		Turf turf = new Turf(4, "Named Place", new CuboidRegion("world", 10, 10, 20, 20), null,
		                      BigDecimal.ZERO, System.currentTimeMillis(), 0);
		Location at = new Location(world, 15, 64, 15);

		when(turfManager.findAt(at)).thenReturn(turf);

		// Build a real PlaceNames and register the provider
		PlaceNames places = new PlaceNames();
		places.register(provider);

		// Query through PlaceNames
		var optionalName = places.locate(at);

		assertTrue(optionalName.isPresent());
		assertEquals("Named Place", optionalName.get());
	}
}
