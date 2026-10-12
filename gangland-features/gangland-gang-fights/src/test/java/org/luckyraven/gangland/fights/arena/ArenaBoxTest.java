package org.luckyraven.gangland.fights.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.region.RegionShape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ArenaBox - inclusive edges, negatives, Y, volume, overlap")
class ArenaBoxTest {

	private final ArenaBox box = new ArenaBox("arena_world", RegionShape.Cuboid.of(0, 60, 0, 9, 69, 9));

	@Test
	@DisplayName("inclusiveEdges")
	void inclusiveEdges() {
		assertTrue(box.contains("arena_world", 0, 60, 0));
		assertTrue(box.contains("arena_world", 9.9, 69.9, 9.9));
		assertFalse(box.contains("arena_world", 10, 65, 5));
		assertFalse(box.contains("arena_world", -0.1, 65, 5));
	}

	@Test
	@DisplayName("negatives: a box below zero keeps its blocks, corners given in any order")
	void negatives() {
		ArenaBox negative = ArenaBox.of("w", -1, 5, -1, -10, 8, -10);

		assertTrue(negative.contains("w", -1, 5, -1));
		assertTrue(negative.contains("w", -9.5, 8.9, -9.5));
		assertTrue(negative.contains("w", -10, 5, -10));
		assertFalse(negative.contains("w", 0, 5, -5));
		assertFalse(negative.contains("w", -10.1, 5, -5));
		assertEquals(10 * 10 * 4, negative.volume());
	}

	@Test
	@DisplayName("yBounds: the box is bounded in height")
	void yBounds() {
		assertTrue(box.contains("arena_world", 5, 60, 5));
		assertTrue(box.contains("arena_world", 5, 69.99, 5));
		assertFalse(box.contains("arena_world", 5, 59.99, 5));
		assertFalse(box.contains("arena_world", 5, 70, 5));
	}

	@Test
	@DisplayName("another world is never inside")
	void otherWorld() {
		assertFalse(box.contains("world", 5, 65, 5));
	}

	@Test
	@DisplayName("volume counts blocks, inclusive")
	void volume() {
		assertEquals(1000, box.volume());
		assertEquals(1, ArenaBox.of("w", 3, 3, 3, 3, 3, 3).volume());
	}

	@Test
	@DisplayName("overlaps: shared block counts, touching faces without a shared block do not, other world never")
	void overlaps() {
		assertTrue(box.overlaps(ArenaBox.of("arena_world", 9, 69, 9, 20, 80, 20)));
		assertFalse(box.overlaps(ArenaBox.of("arena_world", 10, 60, 0, 20, 69, 9)));
		assertFalse(box.overlaps(ArenaBox.of("arena_world", 0, 70, 0, 9, 80, 9)));
		assertFalse(box.overlaps(ArenaBox.of("world", 0, 60, 0, 9, 69, 9)));
		assertTrue(box.overlaps(ArenaBox.of("arena_world", 2, 62, 2, 3, 63, 3)));
	}
}
