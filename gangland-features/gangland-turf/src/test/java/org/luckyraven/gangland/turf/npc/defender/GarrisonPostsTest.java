package org.luckyraven.gangland.turf.npc.defender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Post;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GarrisonPosts - Hold radius and loaded spots")
class GarrisonPostsTest {

	private static final CuboidRegion REGION = new CuboidRegion("world", 0, 0, 40, 40);

	@Test
	@DisplayName("withinHold_horizontalDistance: x/z distance against the radius, y ignored")
	void withinHold_horizontalDistance() {
		Post post = new Post(0, 64, 0);

		assertTrue(GarrisonPosts.withinHold(post, 3, 4, 5));
		assertFalse(GarrisonPosts.withinHold(post, 3, 4.1, 5));
	}

	@Test
	@DisplayName("spots are the block columns of the posts, in the same order")
	void spots_matchPosts() {
		List<int[]> spots = GarrisonPosts.spots(REGION);
		List<Post>  posts = GarrisonPosts.posts(REGION, (x, z) -> 64);

		assertEquals(5, spots.size());
		for (int i = 0; i < 5; i++) {
			assertEquals(posts.get(i).x(), spots.get(i)[0] + 0.5);
			assertEquals(posts.get(i).z(), spots.get(i)[1] + 0.5);
		}
	}

	@Test
	@DisplayName("loaded: a five-element list, null where the chunk is unloaded, and height is never read for an unloaded chunk")
	void loaded_nullForUnloaded_noHeightCall() {
		List<String> heightReads = new ArrayList<>();

		// chunk (0, 0) holds only the first post, block (2, 2)
		List<Post> loaded = GarrisonPosts.loaded(REGION, (x, z) -> x == 0 && z == 0, (x, z) -> {
			heightReads.add(x + "," + z);
			return 64;
		});

		assertEquals(5, loaded.size());
		assertNotNull(loaded.get(0));
		assertEquals(new Post(2.5, 65.0, 2.5), loaded.get(0));
		for (int i = 1; i < 5; i++) {
			assertNull(loaded.get(i));
		}
		assertEquals(List.of("2,2"), heightReads);
	}

	@Test
	@DisplayName("loaded: the predicate gets chunk coordinates (x >> 4, z >> 4), floored for a negative x")
	void loaded_shiftsBlockToChunkCoords() {
		List<String> asked = new ArrayList<>();
		CuboidRegion negative = new CuboidRegion("world", -40, 1000, 0, 1040);

		GarrisonPosts.loaded(negative, (x, z) -> {
			asked.add(x + "," + z);
			return false;
		}, (x, z) -> 64);

		// block x -38 is chunk -3 (a plain division would say -2); block z 1002 is chunk 62
		assertEquals("-3,62", asked.get(0));
		assertEquals(5, asked.size());
	}
}
