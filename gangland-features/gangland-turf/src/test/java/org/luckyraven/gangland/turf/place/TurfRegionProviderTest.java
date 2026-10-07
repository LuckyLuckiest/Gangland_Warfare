package org.luckyraven.gangland.turf.place;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.region.PlaceRegion;
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
		// Different Y coordinates should still find the turf
		Location at1 = new Location(world, 5, 0, 5);
		Location at2 = new Location(world, 5, 64, 5);
		Location at3 = new Location(world, 5, 255, 5);

		when(turfManager.findAt(at1)).thenReturn(turf);
		when(turfManager.findAt(at2)).thenReturn(turf);
		when(turfManager.findAt(at3)).thenReturn(turf);

		List<PlaceRegion> regions1 = provider.regionsAt(at1);
		List<PlaceRegion> regions2 = provider.regionsAt(at2);
		List<PlaceRegion> regions3 = provider.regionsAt(at3);

		assertEquals(1, regions1.size());
		assertEquals(1, regions2.size());
		assertEquals(1, regions3.size());
		// All should return the same turf region
		assertEquals(regions1.get(0).id(), regions2.get(0).id());
		assertEquals(regions2.get(0).id(), regions3.get(0).id());
	}
}
