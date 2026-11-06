package org.luckyraven.gangland.turf.npc.defender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Choice;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Post;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TurfGarrison waves and GarrisonPosts geometry")
class TurfGarrisonTest {

	private static final long COOLDOWN = 20_000L;

	@Test
	@DisplayName("firstWave_cappedByWaveSizeStockAndMaxAlive")
	void firstWave_cappedByWaveSizeStockAndMaxAlive() {
		assertEquals(3, TurfGarrison.toSpawn(10, 0, 6, 3, 3, -1L, 0L, COOLDOWN));
		assertEquals(2, TurfGarrison.toSpawn(2, 0, 6, 3, 3, -1L, 0L, COOLDOWN), "stock caps the wave");
		assertEquals(1, TurfGarrison.toSpawn(10, 5, 6, 3, 3, -1L, 0L, COOLDOWN), "room under Max_Alive caps the wave");
		assertEquals(0, TurfGarrison.toSpawn(0, 0, 6, 3, 3, -1L, 0L, COOLDOWN));
	}

	@Test
	@DisplayName("topUp_afterCooldownOnly: a loss at t spawns nothing before t + cooldown")
	void topUp_afterCooldownOnly() {
		assertEquals(0, TurfGarrison.toSpawn(10, 2, 6, 3, 1, 1_000L, 1_000L + COOLDOWN - 1, COOLDOWN));
		assertEquals(1, TurfGarrison.toSpawn(10, 2, 6, 3, 1, 1_000L, 1_000L + COOLDOWN, COOLDOWN));
		assertEquals(0, TurfGarrison.toSpawn(10, 2, 6, 3, 0, 1_000L, 99_000L, COOLDOWN), "nothing owed, nothing spawned");
	}

	@Test
	@DisplayName("neverAboveMaxAlive: alive never passes Max_Alive however much is owed")
	void neverAboveMaxAlive() {
		assertEquals(0, TurfGarrison.toSpawn(10, 6, 6, 3, 3, -1L, 0L, COOLDOWN));
		assertEquals(0, TurfGarrison.toSpawn(10, 9, 6, 3, 3, -1L, 0L, COOLDOWN));
		assertEquals(3, TurfGarrison.toSpawn(10, 3, 6, 3, 9, -1L, 0L, COOLDOWN), "a wave never exceeds Wave_Size");
	}

	@Test
	@DisplayName("posts_fourInsetCornersAndCentre: corners inset 2 blocks plus the surface centre, y one above the surface")
	void posts_fourInsetCornersAndCentre() {
		List<Post> posts = GarrisonPosts.posts(new CuboidRegion("w", 0, 0, 20, 20), (x, z) -> x + z);

		assertEquals(5, posts.size());
		assertEquals(new Post(2.5, 5.0, 2.5), posts.get(0));
		assertEquals(new Post(18.5, 21.0, 2.5), posts.get(1));
		assertEquals(new Post(2.5, 21.0, 18.5), posts.get(2));
		assertEquals(new Post(18.5, 37.0, 18.5), posts.get(3));
		assertEquals(new Post(10.5, 21.0, 10.5), posts.get(4));
	}

	@Test
	@DisplayName("a tiny region keeps every post inside it")
	void posts_tinyRegion() {
		for (Post post : GarrisonPosts.posts(new CuboidRegion("w", 0, 0, 2, 2), (x, z) -> 64)) {
			assertTrue(post.x() >= 0 && post.x() <= 3 && post.z() >= 0 && post.z() <= 3, post.toString());
		}
	}

	@Test
	@DisplayName("leash_isRegionPlusMargin")
	void leash_isRegionPlusMargin() {
		CuboidRegion region = new CuboidRegion("w", 0, 0, 10, 10);

		assertTrue(GarrisonPosts.withinLeash(5, 5, region, 16));
		assertTrue(GarrisonPosts.withinLeash(-16, 5, region, 16));
		assertFalse(GarrisonPosts.withinLeash(-16.5, 5, region, 16));
		assertTrue(GarrisonPosts.withinLeash(26.9, 5, region, 16));
		assertFalse(GarrisonPosts.withinLeash(27.0, 5, region, 16));
		assertFalse(GarrisonPosts.withinLeash(5, 40, region, 16));
	}

	@Test
	@DisplayName("hiddenPost_preferred_elseFarthestAfterDelay")
	void hiddenPost_preferred_elseFarthestAfterDelay() {
		List<Post> posts = List.of(new Post(0, 0, 0), new Post(10, 0, 0), new Post(20, 0, 0), new Post(30, 0, 0));
		List<double[]> attackers = List.<double[]>of(new double[]{0, 0, 0});

		// 20 and 10 are hidden; the farthest of the hidden ones wins over the farther visible 30
		Choice hidden = GarrisonPosts.chooseSpawn(posts, attackers, (post, a) -> post.x() != 10 && post.x() != 20);
		assertTrue(hidden.hidden());
		assertEquals(posts.get(2), hidden.post());

		Choice none = GarrisonPosts.chooseSpawn(posts, attackers, (post, a) -> true);
		assertFalse(none.hidden(), "nothing hidden: the farthest post, flagged for the 3 s delay");
		assertEquals(posts.get(3), none.post());
	}

	@Test
	@DisplayName("with no attacker in sight every post is hidden and the first post is used")
	void chooseSpawn_noAttackers() {
		List<Post> posts = List.of(new Post(1, 0, 1), new Post(9, 0, 9));
		Choice choice = GarrisonPosts.chooseSpawn(posts, List.of(), (post, a) -> true);
		assertTrue(choice.hidden());
		assertEquals(posts.get(0), choice.post());
	}

	private static TurfDefenderConfig config(int standingCap, int maxAlive) {
		return new TurfDefenderConfig("turf_defender", 32.0, 3, maxAlive, 20, 8, 16, GarrisonComposition.NONE, 0.5, 12, 6.0,
		                              standingCap);
	}

	private static TurfGarrison garrison() {
		return new TurfGarrison(1, new CuboidRegion("w", 0, 0, 40, 40), config(5, 6), "VIP", () -> 0, n -> n, n -> {
		});
	}

	@Test
	@DisplayName("target: peace min(stock, cap, max), contest min(stock, max)")
	void target_peaceAndContest() {
		assertEquals(5, TurfGarrison.target(10, false, config(5, 6)));
		assertEquals(6, TurfGarrison.target(10, true, config(5, 6)));
		assertEquals(2, TurfGarrison.target(2, false, config(5, 6)));
		assertEquals(2, TurfGarrison.target(2, true, config(5, 6)));
		assertEquals(0, TurfGarrison.target(10, false, config(0, 6)));
		assertEquals(6, TurfGarrison.target(10, false, config(8, 6)), "Max_Alive is the ceiling of the cap");
	}

	@Test
	@DisplayName("lowestFree: the lowest number no live guard holds, from 1")
	void lowestFree() {
		assertEquals(2, TurfGarrison.lowestFree(Set.of(1, 3)));
		assertEquals(1, TurfGarrison.lowestFree(Set.of()));
		assertEquals(4, TurfGarrison.lowestFree(List.of(3, 1, 2)));
	}

	@Test
	@DisplayName("remember: posts stays null-free for nearest, and a later pass keeps the spots it already knew")
	void remember_keepsPostsNullFree() {
		TurfGarrison garrison = garrison();
		assertNull(garrison.posts, "null until the first spawn pass");
		Post first = new Post(2.5, 65, 2.5);
		Post third = new Post(2.5, 65, 38.5);

		garrison.remember(Arrays.asList(first, null, null, null, null));
		assertEquals(List.of(first), garrison.posts);

		garrison.remember(Arrays.asList(null, null, third, null, null));
		assertEquals(List.of(first, third), garrison.posts);
		assertFalse(garrison.posts.contains(null));
		assertEquals(first, GarrisonPosts.nearest(garrison.posts, 0, 0));

		Post higher = new Post(2.5, 80, 2.5);
		garrison.remember(Arrays.asList(higher, null, null, null, null));
		assertEquals(List.of(higher, third), garrison.posts, "a fresh height replaces the old one");
	}

	@Test
	@DisplayName("homeOf: a number's own spot, wrapping past five; null for no number or a spot never seen")
	void homeOf_byNumber() {
		TurfGarrison garrison = garrison();
		Post first = new Post(2.5, 65, 2.5);
		Post fifth = new Post(20.5, 65, 20.5);
		garrison.remember(Arrays.asList(first, null, null, null, fifth));

		assertEquals(first, garrison.homeOf(1));
		assertEquals(fifth, garrison.homeOf(5));
		assertEquals(first, garrison.homeOf(6));
		assertNull(garrison.homeOf(2));
		assertNull(garrison.homeOf(0));
	}

	@Test
	@DisplayName("adopt copies the config, tag, owner and price of a fresh garrison")
	void adopt_copiesLiveFields() {
		TurfGarrison garrison = garrison();
		TurfDefenderConfig config = config(2, 9);
		TurfGarrison fresh = new TurfGarrison(1, new CuboidRegion("w", 0, 0, 40, 40), config, "NEW", () -> 0, n -> n, n -> {
		});
		fresh.funded(7, 3000);

		garrison.adopt(fresh);

		assertEquals(config, garrison.config);
		assertEquals("NEW", garrison.tag);
		assertEquals(7, garrison.ownerGangId);
		assertEquals(3000, garrison.guardPrice);
	}

	@Test
	@DisplayName("nearest returns the post closest to a point")
	void nearest_picksClosest() {
		List<Post> posts = List.of(new Post(0, 0, 0), new Post(10, 0, 0));
		assertEquals(posts.get(1), GarrisonPosts.nearest(posts, 8, 1));
	}
}
