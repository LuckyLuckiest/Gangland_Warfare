package org.luckyraven.gangland.copsncrooks.npc.police.perch;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.OverwatchSettings;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PerchFinder} (P-OVW-1): the roof columns overlooking the last known spot. The world is a mock driven by a
 * highest-block function, a chunk-loaded predicate and a blocked-lane predicate on the ray's start block.
 *
 * <p>With the default 8 columns the angles are 0, 45, ... 315 degrees and the radii alternate 10 / 20 blocks, so a
 * column is tall where {@code blockX >= 1}: angles 0 (10 blocks), 45 and 315 (20 blocks).
 */
@DisplayName("PerchFinder")
class PerchFinderTest {

	private static final OverwatchSettings SETTINGS = OverwatchSettings.DEFAULT;
	private static final int               TALL     = 70;
	private static final int               LOW      = 63;

	private static World world(IntBinaryOperator highest, BiPredicate<Integer, Integer> loaded,
	                           BiPredicate<Integer, Integer> blocked) {
		World world = mock(World.class);
		when(world.getHighestBlockYAt(anyInt(), anyInt())).thenAnswer(
				i -> highest.applyAsInt(i.getArgument(0), i.getArgument(1)));
		when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(i -> loaded.test(i.getArgument(0), i.getArgument(1)));
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER),
		                          anyBoolean())).thenAnswer(i -> {
			Location start = i.getArgument(0);
			return blocked.test(start.getBlockX(), start.getBlockZ()) ? mock(RayTraceResult.class) : null;
		});
		return world;
	}

	private static World tallEast() {
		return world((x, z) -> x >= 1 ? TALL : LOW, (cx, cz) -> true, (x, z) -> false);
	}

	private static Location lastKnown(World world) {
		return new Location(world, 0, 64, 0);
	}

	@Test
	@DisplayName("8 columns, 3 tall with a clear lane: 3 perches, nearest first, standing on the roof")
	void keepsOnlyHighClearColumns() {
		World world = tallEast();

		List<Location> perches = PerchFinder.find(lastKnown(world), SETTINGS);

		assertEquals(3, perches.size());
		assertEquals(10.0, perches.get(0).getX(), 1.0E-6);
		double previous = 0;
		for (Location perch : perches) {
			double distance = Math.hypot(perch.getX(), perch.getZ());
			assertTrue(distance >= previous, "nearest first");
			assertEquals(TALL + 1, perch.getY(), 1.0E-6);
			previous = distance;
		}
	}

	@Test
	@DisplayName("radii alternate between Ring_Min and Ring_Max")
	void ringRadiiAlternate() {
		World world = world((x, z) -> TALL, (cx, cz) -> true, (x, z) -> false);

		List<Location> perches = PerchFinder.find(lastKnown(world), SETTINGS);

		assertEquals(8, perches.size());
		List<Long> radii = new ArrayList<>();
		for (Location perch : perches) radii.add(Math.round(Math.hypot(perch.getX(), perch.getZ())));
		assertEquals(4, radii.stream().filter(r -> r == 10L).count());
		assertEquals(4, radii.stream().filter(r -> r == 20L).count());
	}

	@Test
	@DisplayName("a column whose chunk is not loaded is skipped")
	void unloadedChunkSkipped() {
		// the 315-degree column (14, -14) is in chunk z -1; the others are in chunk z 0
		World world = world((x, z) -> x >= 1 ? TALL : LOW, (cx, cz) -> cz != -1, (x, z) -> false);

		List<Location> perches = PerchFinder.find(lastKnown(world), SETTINGS);

		assertEquals(2, perches.size());
		assertTrue(perches.stream().allMatch(perch -> perch.getZ() >= 0));
	}

	@Test
	@DisplayName("a column whose sight lane to the last known spot is blocked is skipped")
	void blockedLaneSkipped() {
		World world = world((x, z) -> x >= 1 ? TALL : LOW, (cx, cz) -> true, (x, z) -> x >= 12);

		List<Location> perches = PerchFinder.find(lastKnown(world), SETTINGS);

		assertEquals(1, perches.size());
		assertEquals(10.0, perches.get(0).getX(), 1.0E-6);
	}

	@Test
	@DisplayName("the ray runs eye to eye: 1.62 above the perch toward 1.62 above the last known spot")
	void rayRunsEyeToEye() {
		World world = world((x, z) -> x >= 1 && x < 12 ? TALL : LOW, (cx, cz) -> true, (x, z) -> false);

		PerchFinder.find(lastKnown(world), SETTINGS);

		ArgumentCaptor<Location> start = ArgumentCaptor.forClass(Location.class);
		ArgumentCaptor<Vector>   dir   = ArgumentCaptor.forClass(Vector.class);
		verify(world).rayTraceBlocks(start.capture(), dir.capture(), anyDouble(), eq(FluidCollisionMode.NEVER),
		                             anyBoolean());
		assertEquals(TALL + 1 + 1.62, start.getValue().getY(), 1.0E-6);
		// from (10, 72.62, 0) toward (0, 65.62, 0): west and down
		assertTrue(dir.getValue().getX() < 0 && dir.getValue().getY() < 0);
	}

	@Test
	@DisplayName("Min_Height is inclusive: perch y 68 over a spot at 64 passes, 67 does not")
	void minHeightBoundary() {
		World at68 = world((x, z) -> x >= 1 ? 67 : LOW, (cx, cz) -> true, (x, z) -> false);
		World at67 = world((x, z) -> x >= 1 ? 66 : LOW, (cx, cz) -> true, (x, z) -> false);

		assertEquals(3, PerchFinder.find(lastKnown(at68), SETTINGS).size());
		assertTrue(PerchFinder.find(lastKnown(at67), SETTINGS).isEmpty());
	}

	@Test
	@DisplayName("Columns bounds how many candidates are tried")
	void columnsLimitCandidates() {
		World             world = world((x, z) -> TALL, (cx, cz) -> true, (x, z) -> false);
		OverwatchSettings four  = new OverwatchSettings(true, 4, "Marksman", 10.0, 20.0, 4, 4, 48.0, 3, 20, 120);

		assertEquals(4, PerchFinder.find(lastKnown(world), four).size());
	}

	@Test
	@DisplayName("a last known spot without a world yields no perches")
	void nullWorldYieldsNothing() {
		assertTrue(PerchFinder.find(new Location(null, 0, 64, 0), SETTINGS).isEmpty());
	}

	@Test
	@DisplayName("flat ground (nothing tall) yields no perches")
	void flatGroundYieldsNothing() {
		World world = world((x, z) -> LOW, (cx, cz) -> true, (x, z) -> false);

		assertTrue(PerchFinder.find(lastKnown(world), SETTINGS).isEmpty());
	}
}
