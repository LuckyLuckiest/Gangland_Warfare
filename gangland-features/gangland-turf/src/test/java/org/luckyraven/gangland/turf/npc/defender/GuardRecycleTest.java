package org.luckyraven.gangland.turf.npc.defender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Post;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GuardRecycle - a stuck, unseen guard reappears at another post")
class GuardRecycleTest {

	private static final int  SECONDS = 12;
	private static final long NOW     = 100_000L;

	@Test
	@DisplayName("unreachableAndUnseen_recycled")
	void unreachableAndUnseen_recycled() {
		assertTrue(GuardRecycle.due(12_001L, SECONDS, -1L, NOW, false));
	}

	@Test
	@DisplayName("seen_notRecycled: an attacker with a line to it keeps it")
	void seen_notRecycled() {
		assertFalse(GuardRecycle.due(60_000L, SECONDS, -1L, NOW, true));
	}

	@Test
	@DisplayName("underThreshold_notRecycled: exactly Recycle_Seconds is not yet over it")
	void underThreshold_notRecycled() {
		assertFalse(GuardRecycle.due(12_000L, SECONDS, -1L, NOW, false));
		assertFalse(GuardRecycle.due(0L, SECONDS, -1L, NOW, false));
	}

	@Test
	@DisplayName("once per Recycle_Seconds: a guard recycled a moment ago is left alone, then due again")
	void oncePerWindow() {
		assertFalse(GuardRecycle.due(30_000L, SECONDS, NOW - 5_000L, NOW, false));
		assertTrue(GuardRecycle.due(30_000L, SECONDS, NOW - 12_000L, NOW, false));
	}

	@Test
	@DisplayName("recycle off at 0 seconds")
	void off() {
		assertFalse(GuardRecycle.due(999_999L, 0, -1L, NOW, false));
	}

	@Test
	@DisplayName("elsewhere: never the post the guard was stuck at, hidden ones first; one post is all there is")
	void elsewhere_skipsTheStuckPost() {
		Post a = new Post(0, 64, 0);
		Post b = new Post(50, 64, 0);
		Post c = new Post(0, 64, 50);
		List<double[]> attackers = List.of(new double[]{25, 65, 25});

		Post chosen = GuardRecycle.elsewhere(List.of(a, b, c), 1, 1, attackers, (post, eye) -> post.equals(c));
		assertEquals(b, chosen, "c is seen, a is the stuck one: b is the hidden other post");

		assertEquals(a, GuardRecycle.elsewhere(List.of(a), 1, 1, attackers, (post, eye) -> false));
	}
}
