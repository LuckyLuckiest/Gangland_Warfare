package org.luckyraven.gangland.data.teleportation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link WaypointRegionProvider}: a gang's GANG waypoint becomes a hideout place owned by that gang. Radius 0 means an
 * 8-block sphere, a huge radius is capped at 64, and waypoints of any other type or without a gang publish nothing.
 */
@DisplayName("WaypointRegionProvider - gang waypoints as hideout places")
class WaypointRegionProviderTest {

	private BukkitStatics   bukkit;
	private World           world;
	private WaypointManager manager;
	private WaypointRegionProvider provider;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		world  = mock(World.class);
		when(world.getName()).thenReturn("world");
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

		manager  = new WaypointManager(mock(Gangland.class), mock(GanglandDatabase.class),
		                               mock(PermissionManager.class));
		provider = new WaypointRegionProvider(manager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private Waypoint add(Waypoint.WaypointType type, int gangId, double radius) {
		Waypoint waypoint = new Waypoint("base", "gangland");
		waypoint.setType(type);
		waypoint.setGangId(gangId);
		waypoint.setRadius(radius);
		waypoint.setCoordinates("world", 0, 64, 0, 0F, 0F);
		manager.add(waypoint);
		return waypoint;
	}

	private Location at(double x) {
		return new Location(world, x, 64, 0);
	}

	@Test
	@DisplayName("radius 0 is an 8-block sphere owned by the gang and tagged hideout")
	void radiusZero_isAnEightBlockSphere() {
		Waypoint waypoint = add(Waypoint.WaypointType.GANG, 7, 0);

		List<PlaceRegion> inside = provider.regionsAt(at(7.9));
		List<PlaceRegion> outside = provider.regionsAt(at(8.1));

		assertEquals(1, inside.size());
		PlaceRegion region = inside.get(0);
		assertEquals(7, region.ownerGangId());
		assertEquals("base", region.name());
		assertEquals("waypoint:" + waypoint.getUsedId(), region.id());
		assertTrue(region.hasTag(PlaceRegion.TAG_HIDEOUT));
		assertTrue(outside.isEmpty());
	}

	@Test
	@DisplayName("a radius of 500 is capped at 64")
	void hugeRadius_isCappedAt64() {
		add(Waypoint.WaypointType.GANG, 7, 500);

		assertEquals(1, provider.regionsAt(at(63.9)).size());
		assertTrue(provider.regionsAt(at(64.1)).isEmpty());
	}

	@Test
	@DisplayName("a radius between 0 and the cap is used as set")
	void smallRadius_isUsed() {
		add(Waypoint.WaypointType.GANG, 7, 20);

		assertEquals(1, provider.regionsAt(at(19.9)).size());
		assertTrue(provider.regionsAt(at(20.1)).isEmpty());
	}

	@Test
	@DisplayName("non-GANG waypoints and gangless GANG waypoints publish nothing")
	void nonGang_isIgnored() {
		add(Waypoint.WaypointType.HOSPITAL, 7, 0);
		add(Waypoint.WaypointType.SPAWN, 7, 0);
		add(Waypoint.WaypointType.GANG, -1, 0);

		assertTrue(provider.regionsAt(at(0)).isEmpty());
	}

}
