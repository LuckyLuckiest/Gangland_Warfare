package org.luckyraven.gangland.data.teleportation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link WaypointManager#nearest}: the closest waypoint of one type in the caller's world. Another world is never a
 * candidate however close its coordinates, a waypoint whose world is not loaded is skipped, and a different type is
 * not a hospital.
 */
@DisplayName("WaypointManager - nearest waypoint of a type")
class WaypointManagerNearestTest {

	private BukkitStatics   bukkit;
	private World           overworld;
	private World           nether;
	private WaypointManager manager;

	@BeforeEach
	void setUp() {
		bukkit    = BukkitStatics.install();
		overworld = mock(World.class);
		nether    = mock(World.class);
		when(overworld.getName()).thenReturn("world");
		when(nether.getName()).thenReturn("nether");
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(overworld);
		bukkit.statics().when(() -> Bukkit.getWorld("nether")).thenReturn(nether);

		manager = new WaypointManager(mock(Gangland.class), mock(GanglandDatabase.class),
		                              mock(PermissionManager.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private Waypoint add(String name, Waypoint.WaypointType type, String world, double x) {
		Waypoint waypoint = new Waypoint(name, "gangland");
		waypoint.setType(type);
		waypoint.setCoordinates(world, x, 64, 0, 0F, 0F);
		manager.add(waypoint);
		return waypoint;
	}

	@Test
	@DisplayName("the nearest hospital of the same world wins")
	void nearestWins() {
		add("far", Waypoint.WaypointType.HOSPITAL, "world", 500);
		Waypoint near = add("near", Waypoint.WaypointType.HOSPITAL, "world", 40);

		assertSame(near, manager.nearest(new Location(overworld, 0, 64, 0), Waypoint.WaypointType.HOSPITAL));
	}

	@Test
	@DisplayName("another world is never a candidate, however close")
	void otherWorld_isSkipped() {
		add("closer-but-nether", Waypoint.WaypointType.HOSPITAL, "nether", 1);
		Waypoint ward = add("ward", Waypoint.WaypointType.HOSPITAL, "world", 900);

		assertSame(ward, manager.nearest(new Location(overworld, 0, 64, 0), Waypoint.WaypointType.HOSPITAL));
	}

	@Test
	@DisplayName("a waypoint whose world is not loaded is skipped")
	void unloadedWorld_isSkipped() {
		World gone = mock(World.class);
		when(gone.getName()).thenReturn("gone");
		add("lost", Waypoint.WaypointType.HOSPITAL, "gone", 1);

		assertNull(manager.nearest(new Location(gone, 0, 64, 0), Waypoint.WaypointType.HOSPITAL));
	}

	@Test
	@DisplayName("only the asked type counts, and no match is null")
	void otherType_isNotAHospital() {
		add("spawn", Waypoint.WaypointType.SPAWN, "world", 1);

		assertNull(manager.nearest(new Location(overworld, 0, 64, 0), Waypoint.WaypointType.HOSPITAL));
	}

}
