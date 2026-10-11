package org.luckyraven.gangland.fights.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.region.RegionShape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ArenaBox - inclusive edges, same world only")
class ArenaBoxTest {

	private final ArenaBox box = new ArenaBox("arena_world", RegionShape.Cuboid.of(0, 60, 0, 9, 69, 9));

	@Test
	@DisplayName("both corners are inside, one block past is outside")
	void edgesAreInclusive() {
		assertTrue(box.contains("arena_world", 0, 60, 0));
		assertTrue(box.contains("arena_world", 9.9, 69.9, 9.9));
		assertFalse(box.contains("arena_world", 10, 65, 5));
		assertFalse(box.contains("arena_world", -0.1, 65, 5));
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
	}
}
