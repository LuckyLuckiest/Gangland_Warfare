package org.luckyraven.gangland.copsncrooks.wanted.trespass;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TrespassTracker}: the edge trigger (only an entry is reported) and the per player and region repeat throttle
 * behind P-CRIME-4 (0.20.0).
 */
@DisplayName("TrespassTracker")
class TrespassTrackerTest {

	private static final long REPEAT = 60_000L;

	private final TrespassTracker tracker = new TrespassTracker();
	private final UUID            alice   = UUID.randomUUID();
	private final UUID            bob     = UUID.randomUUID();

	@Test
	@DisplayName("first sight of a region is an entry, standing in it is not")
	void entered_reportsOnlyNewRegions() {
		assertEquals(Set.of("a"), tracker.entered(alice, Set.of("a")));
		assertEquals(Set.of(), tracker.entered(alice, Set.of("a")));
	}

	@Test
	@DisplayName("moving from region a into a and b reports only b")
	void entered_overlapReportsOnlyTheAddition() {
		tracker.entered(alice, Set.of("a"));

		assertEquals(Set.of("b"), tracker.entered(alice, Set.of("a", "b")));
	}

	@Test
	@DisplayName("leaving and coming back is an entry again")
	void entered_leaveThenReenter() {
		tracker.entered(alice, Set.of("a"));
		assertEquals(Set.of(), tracker.entered(alice, Set.of()));

		assertEquals(Set.of("a"), tracker.entered(alice, Set.of("a")));
	}

	@Test
	@DisplayName("players do not share an inside-set")
	void entered_isPerPlayer() {
		tracker.entered(alice, Set.of("a"));

		assertEquals(Set.of("a"), tracker.entered(bob, Set.of("a")));
	}

	@Test
	@DisplayName("clear forgets the inside-set so the next sighting is an entry")
	void clear_forgetsInside() {
		tracker.entered(alice, Set.of("a"));
		tracker.clear(alice);

		assertEquals(Set.of("a"), tracker.entered(alice, Set.of("a")));
	}

	@Test
	@DisplayName("clear leaves the throttle alone: a relog does not buy a free crime")
	void clear_keepsThrottle() {
		assertTrue(tracker.claim(alice, "a", 1_000L, REPEAT));
		tracker.clear(alice);

		assertFalse(tracker.claim(alice, "a", 2_000L, REPEAT));
	}

	@Test
	@DisplayName("claim: first is granted, inside the window refused, at the window granted again")
	void claim_throttlesPerWindow() {
		assertTrue(tracker.claim(alice, "a", 0L, REPEAT));
		assertFalse(tracker.claim(alice, "a", 30_000L, REPEAT));
		assertFalse(tracker.claim(alice, "a", 59_999L, REPEAT));
		assertTrue(tracker.claim(alice, "a", 60_000L, REPEAT));
	}

	@Test
	@DisplayName("a refused claim does not extend the window")
	void claim_refusalDoesNotRestartWindow() {
		tracker.claim(alice, "a", 0L, REPEAT);
		tracker.claim(alice, "a", 50_000L, REPEAT);

		assertTrue(tracker.claim(alice, "a", 61_000L, REPEAT));
	}

	@Test
	@DisplayName("claims are per region and per player")
	void claim_isPerRegionAndPlayer() {
		assertTrue(tracker.claim(alice, "a", 0L, REPEAT));

		assertTrue(tracker.claim(alice, "b", 1L, REPEAT));
		assertTrue(tracker.claim(bob, "a", 1L, REPEAT));
	}
}
