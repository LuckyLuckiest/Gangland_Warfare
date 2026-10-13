package org.luckyraven.gangland.copsncrooks.npc.police.breach;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.breach.DoorFinder.BlockLookup;
import org.luckyraven.gangland.copsncrooks.npc.police.breach.DoorFinder.Door;
import org.luckyraven.gangland.copsncrooks.npc.police.breach.DoorFinder.Probe;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DoorFinder}: the pure door scan (lower door halves within a cube, nearest to the suspect, stack cell on the
 * far side of the door's axis) and the indoors test. A flat world: solid at y 63 and below, air above.
 */
@DisplayName("DoorFinder")
class DoorFinderTest {

	private final World    world   = mock(World.class);
	private final Location suspect = new Location(world, 0.5, 64, 0.5);

	private final Map<String, Probe> overrides = new HashMap<>();

	private static String key(int x, int y, int z) {
		return x + "," + y + "," + z;
	}

	private BlockLookup lookup() {
		return (x, y, z) -> {
			Probe set = overrides.get(key(x, y, z));
			if (set != null) return set;
			return y <= 63 ? Probe.SOLID : Probe.AIR;
		};
	}

	private void door(int x, int y, int z, BlockFace facing) {
		overrides.put(key(x, y, z), new Probe(true, facing, false, false));
	}

	@Test
	@DisplayName("a door 4 blocks east of the suspect gives the cell on the outside (east) of it")
	void findsNearestDoorAndOutsideCell() {
		door(4, 64, 0, BlockFace.EAST);

		Optional<Door> found = DoorFinder.find(suspect, 6, lookup());

		assertTrue(found.isPresent());
		assertEquals(4, found.get().door().getBlockX());
		assertEquals(64, found.get().door().getBlockY());
		assertEquals(0, found.get().door().getBlockZ());
		assertEquals(5, found.get().stackCell().getBlockX());
		assertEquals(64, found.get().stackCell().getBlockY());
		assertEquals(0, found.get().stackCell().getBlockZ());
		assertSame(world, found.get().stackCell().getWorld());
	}

	@Test
	@DisplayName("a north-south door puts the cell on the far side along z")
	void northFacingDoor_cellOnTheFarZSide() {
		door(0, 64, -4, BlockFace.NORTH);

		Door found = DoorFinder.find(suspect, 6, lookup()).orElseThrow();

		assertEquals(-5, found.stackCell().getBlockZ());
		assertEquals(0, found.stackCell().getBlockX());
	}

	@Test
	@DisplayName("with two doors the one nearer the suspect wins")
	void twoDoors_nearestWins() {
		door(4, 64, 0, BlockFace.EAST);
		door(-3, 64, 0, BlockFace.EAST);

		Door found = DoorFinder.find(suspect, 6, lookup()).orElseThrow();

		assertEquals(-3, found.door().getBlockX());
		// suspect is at x 0.5: of -2 and -4 the cell outside the door is the farther one
		assertEquals(-4, found.stackCell().getBlockX());
	}

	@Test
	@DisplayName("a door 7 blocks away is outside Door_Radius 6")
	void noDoorWithinRadius_empty() {
		door(7, 64, 0, BlockFace.EAST);

		assertTrue(DoorFinder.find(suspect, 6, lookup()).isEmpty());
	}

	@Test
	@DisplayName("a door exactly Door_Radius away is found")
	void doorAtRadiusEdge_found() {
		door(6, 64, 0, BlockFace.EAST);

		assertTrue(DoorFinder.find(suspect, 6, lookup()).isPresent());
	}

	@Test
	@DisplayName("no door at all: empty")
	void noDoor_empty() {
		assertTrue(DoorFinder.find(suspect, 6, lookup()).isEmpty());
	}

	@Test
	@DisplayName("an upper door half (or any non-door) is never a door")
	void upperHalf_isIgnored() {
		overrides.put(key(4, 64, 0), new Probe(false, BlockFace.EAST, false, false));

		assertTrue(DoorFinder.find(suspect, 6, lookup()).isEmpty());
	}

	@Test
	@DisplayName("a far-side cell with no headroom is not a stack cell: the next door is used")
	void cellWithoutHeadroom_fallsBackToNextDoor() {
		door(3, 64, 0, BlockFace.EAST);
		overrides.put(key(4, 65, 0), Probe.SOLID);
		door(0, 64, 5, BlockFace.SOUTH);

		Door found = DoorFinder.find(suspect, 6, lookup()).orElseThrow();

		assertEquals(5, found.door().getBlockZ());
		assertEquals(6, found.stackCell().getBlockZ());
	}

	@Test
	@DisplayName("a far-side cell without a solid floor below is skipped")
	void cellWithoutFloor_skipped() {
		door(4, 64, 0, BlockFace.EAST);
		overrides.put(key(5, 63, 0), Probe.AIR);

		assertTrue(DoorFinder.find(suspect, 6, lookup()).isEmpty());
	}

	@Test
	@DisplayName("a far-side cell blocked at the feet is skipped")
	void cellBlockedAtFeet_skipped() {
		door(4, 64, 0, BlockFace.EAST);
		overrides.put(key(5, 64, 0), Probe.SOLID);

		assertTrue(DoorFinder.find(suspect, 6, lookup()).isEmpty());
	}

	@Test
	@DisplayName("the scan reads at most (2r+1)^3 blocks from the cube, nothing outside it")
	void scanIsBoundedByTheCube() {
		AtomicInteger calls = new AtomicInteger();
		int[] minMax = {Integer.MAX_VALUE, Integer.MIN_VALUE};
		BlockLookup counting = (x, y, z) -> {
			calls.incrementAndGet();
			minMax[0] = Math.min(minMax[0], x);
			minMax[1] = Math.max(minMax[1], x);
			return Probe.AIR;
		};

		assertTrue(DoorFinder.find(suspect, 6, counting).isEmpty());

		assertTrue(calls.get() <= 13 * 13 * 13, "lookups: " + calls.get());
		assertTrue(minMax[0] >= -6 && minMax[1] <= 6, "x range " + minMax[0] + ".." + minMax[1]);
	}

	@Test
	@DisplayName("a suspect location with no world gives no door and never reads blocks")
	void nullWorld_empty() {
		AtomicInteger calls = new AtomicInteger();

		Optional<Door> found = DoorFinder.find(new Location(null, 0, 64, 0), 6, (x, y, z) -> {
			calls.incrementAndGet();
			return Probe.AIR;
		});

		assertTrue(found.isEmpty());
		assertEquals(0, calls.get());
	}

	@Test
	@DisplayName("highest block 3 above the feet is indoors (more than 2); 2 above is not")
	void indoorsNeedsMoreThanRoofBlocks() {
		when(world.getHighestBlockYAt(0, 0)).thenReturn(67);
		assertTrue(DoorFinder.indoors(suspect, 2));

		when(world.getHighestBlockYAt(0, 0)).thenReturn(66);
		assertFalse(DoorFinder.indoors(suspect, 2));
	}

	@Test
	@DisplayName("open sky (highest block at or below the feet) is not indoors, even with Roof_Blocks 0")
	void openSky_notIndoors() {
		when(world.getHighestBlockYAt(0, 0)).thenReturn(63);

		assertFalse(DoorFinder.indoors(suspect, 0));
	}

	@Test
	@DisplayName("indoors reads the column of the block the suspect stands in, negative coordinates included")
	void indoorsUsesBlockColumn() {
		Location neg = new Location(world, -0.5, 64, -3.2);
		when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(0);
		when(world.getHighestBlockYAt(-1, -4)).thenReturn(70);

		assertTrue(DoorFinder.indoors(neg, 2));
	}

	@Test
	@DisplayName("indoors with no world is false")
	void indoorsNullWorld_false() {
		assertFalse(DoorFinder.indoors(new Location(null, 0, 64, 0), 2));
	}
}
