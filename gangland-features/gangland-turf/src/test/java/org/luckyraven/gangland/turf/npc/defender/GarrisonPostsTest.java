package org.luckyraven.gangland.turf.npc.defender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Post;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GarrisonPosts - Hold radius")
class GarrisonPostsTest {

	@Test
	@DisplayName("withinHold_horizontalDistance: x/z distance against the radius, y ignored")
	void withinHold_horizontalDistance() {
		Post post = new Post(0, 64, 0);

		assertTrue(GarrisonPosts.withinHold(post, 3, 4, 5));
		assertFalse(GarrisonPosts.withinHold(post, 3, 4.1, 5));
	}
}
