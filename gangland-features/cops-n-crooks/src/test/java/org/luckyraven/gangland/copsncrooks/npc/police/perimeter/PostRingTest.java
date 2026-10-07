package org.luckyraven.gangland.copsncrooks.npc.police.perimeter;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PostRing#find}: the eight ring candidates in their fixed order, filtered by chunk, floor and clear lane, then
 * thinned to a 90-degree spacing. The world is a flat stub (floor below y 64) keyed on coordinates, never on call order.
 */
@DisplayName("PostRing")
class PostRingTest {

	static final double LANE = 16.0;

	/** A flat world: solid below y 64, air from 64 up; every chunk loaded and no lane blocked. */
	static World flatWorld() {
		return world((x, z) -> true, (x, z) -> false);
	}

	/** {@code loaded(chunkX, chunkZ)} and {@code blocked(startBlockX, startBlockZ)} decide the two filters. */
	static World world(BiPredicate<Integer, Integer> loaded, BiPredicate<Integer, Integer> blocked) {
		World world = mock(World.class);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(i -> loaded.test(i.getArgument(0), i.getArgument(1)));
		when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(i -> {
			Block block = mock(Block.class);
			when(block.isPassable()).thenReturn((int) i.getArgument(1) >= 64);
			return block;
		});
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER),
		                          anyBoolean())).thenAnswer(i -> {
			Location start = i.getArgument(0);
			return blocked.test(start.getBlockX(), start.getBlockZ()) ? mock(RayTraceResult.class) : null;
		});
		return world;
	}

	private static Location centre(World world) {
		return new Location(world, 0, 64, 0);
	}

	@Test
	@DisplayName("the first spots are the 45 and 225 degree corners, then 135")
	void find_ordersTheAnglesFortyFiveTwoTwentyFiveOneThirtyFive() {
		World world = flatWorld();

		List<Location> spots = PostRing.find(centre(world), 32, 3, LANE);

		assertEquals(3, spots.size());
		assertTrue(spots.get(0).getX() > 0 && spots.get(0).getZ() > 0);
		assertTrue(spots.get(1).getX() < 0 && spots.get(1).getZ() < 0);
		assertTrue(spots.get(2).getX() < 0 && spots.get(2).getZ() > 0);
		assertEquals(32, Math.hypot(spots.get(0).getX(), spots.get(0).getZ()), 1.0E-6);
	}

	@Test
	@DisplayName("a spot in an unloaded chunk is skipped")
	void find_skipsUnloadedChunks() {
		// the 45-degree spot (22.6, 22.6) sits in chunk (1, 1)
		World world = world((cx, cz) -> !(cx == 1 && cz == 1), (x, z) -> false);

		List<Location> spots = PostRing.find(centre(world), 32, 2, LANE);

		assertEquals(2, spots.size());
		assertTrue(spots.get(0).getX() < 0 && spots.get(0).getZ() < 0, "225 first");
		assertTrue(spots.get(1).getX() < 0 && spots.get(1).getZ() > 0, "then 135");
	}

	@Test
	@DisplayName("a spot whose lane toward the centre is blocked is skipped")
	void find_skipsBlockedLanes() {
		World world = world((cx, cz) -> true, (x, z) -> x > 0 && z > 0);

		List<Location> spots = PostRing.find(centre(world), 32, 2, LANE);

		assertEquals(2, spots.size());
		assertTrue(spots.get(0).getX() < 0 && spots.get(0).getZ() < 0);
	}

	@Test
	@DisplayName("a spot with no floor within 8 blocks is skipped")
	void find_skipsSpotsWithoutAFloor() {
		World world = flatWorld();
		// a hole under the 45-degree spot: nothing solid anywhere in the column
		when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(i -> {
			Block block = mock(Block.class);
			int   x     = i.getArgument(0);
			int   z     = i.getArgument(2);
			when(block.isPassable()).thenReturn((x > 0 && z > 0) || (int) i.getArgument(1) >= 64);
			return block;
		});

		List<Location> spots = PostRing.find(centre(world), 32, 1, LANE);

		assertEquals(1, spots.size());
		assertTrue(spots.get(0).getX() < 0 && spots.get(0).getZ() < 0);
	}

	@Test
	@DisplayName("spots keep a 90 degree spacing: asking for eight gives the four corners")
	void find_keepsNinetyDegreeSpacing() {
		World world = flatWorld();

		List<Location> spots = PostRing.find(centre(world), 32, 8, LANE);

		assertEquals(4, spots.size());
	}

	@Test
	@DisplayName("count caps the result and zero asks for nothing")
	void find_honoursCount() {
		World world = flatWorld();

		assertEquals(1, PostRing.find(centre(world), 32, 1, LANE).size());
		assertTrue(PostRing.find(centre(world), 32, 0, LANE).isEmpty());
	}

	@Test
	@DisplayName("a spot stands on the floor found near the centre height")
	void find_standsOnTheFloor() {
		World world = flatWorld();

		Location spot = PostRing.find(centre(world), 32, 1, LANE).get(0);

		assertEquals(64, spot.getY(), 1.0E-6);
	}
}
