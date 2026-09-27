package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.12 F4: {@link RosterPlanner} is pure arithmetic, so every case here runs off the main thread with no Bukkit or
 * Citizens types involved.
 */
@DisplayName("RosterPlanner")
class RosterPlannerTest {

	@Test
	@DisplayName("deficits is empty for a null or empty roster")
	void deficits_emptyRoster() {
		assertTrue(RosterPlanner.deficits(null, Map.of(), 0, 8).isEmpty());
		assertTrue(RosterPlanner.deficits(Map.of(), Map.of(), 0, 8).isEmpty());
	}

	@Test
	@DisplayName("deficits is empty once the group is already at maxTotal")
	void deficits_emptyAtCap() {
		Map<Integer, Integer> roster = Map.of(1, 2, 2, 2);

		assertTrue(RosterPlanner.deficits(roster, Map.of(), 4, 4).isEmpty());
		assertTrue(RosterPlanner.deficits(roster, Map.of(), 5, 4).isEmpty(), "over the cap is also empty");
	}

	@Test
	@DisplayName("deficits reports missing counts per tier, ascending tier order")
	void deficits_missingCounts() {
		Map<Integer, Integer> roster        = new LinkedHashMap<>();
		roster.put(2, 2);
		roster.put(3, 2);

		Map<Integer, Integer> currentByTier = Map.of(2, 1);

		Map<Integer, Integer> deficits = RosterPlanner.deficits(roster, currentByTier, 1, 8);

		assertEquals(Map.of(2, 1, 3, 2), deficits);
		assertEquals(java.util.List.of(2, 3), java.util.List.copyOf(deficits.keySet()), "ascending tier order");
	}

	@Test
	@DisplayName("deficits ignores tiers already at or above their roster count")
	void deficits_skipsSatisfiedTiers() {
		Map<Integer, Integer> roster        = Map.of(1, 2);
		Map<Integer, Integer> currentByTier = Map.of(1, 3);

		assertTrue(RosterPlanner.deficits(roster, currentByTier, 3, 8).isEmpty());
	}

	@Test
	@DisplayName("deficits clamps its total so currentTotal + deficits never exceeds maxTotal")
	void deficits_clampsToMaxTotal() {
		Map<Integer, Integer> roster        = new LinkedHashMap<>();
		roster.put(1, 2);
		roster.put(2, 4);

		Map<Integer, Integer> deficits = RosterPlanner.deficits(roster, Map.of(), 5, 6);

		int total = deficits.values().stream().mapToInt(Integer::intValue).sum();
		assertEquals(1, total, "only one more slot is free (5 current, 6 max)");
		assertEquals(Map.of(1, 1), deficits, "lower tier is filled first");
	}

	@Test
	@DisplayName("deficits clamps to the roster's own total, not just maxTotal, for the single-tier fallback roster")
	void deficits_clampsToRosterTotal_singleTierFallback() {
		// CopSpawnManager#getRosterForWantedLevel single-tier fallback: 1 star -> tier 1 x2, 2 stars -> tier 2 x3.
		// 2 leftover tier-1 cops from 1 star must count against the 2-star roster's total (3), not stack a full new
		// squad on top of them (0.12 F4 regression).
		Map<Integer, Integer> roster        = Map.of(2, 3);
		Map<Integer, Integer> currentByTier = Map.of(1, 2);

		Map<Integer, Integer> deficits = RosterPlanner.deficits(roster, currentByTier, 2, 10);

		assertEquals(Map.of(2, 1), deficits, "only 1 more tier-2 cop is spawned, matching the pre-0.12 total of 3");
	}

	@Test
	@DisplayName("deficits still honors a shipped multi-tier roster larger than the leftover total")
	void deficits_multiTierRoster_notClampedBelowItsOwnTotal() {
		Map<Integer, Integer> roster        = new LinkedHashMap<>();
		roster.put(1, 2);
		roster.put(2, 1);

		Map<Integer, Integer> deficits = RosterPlanner.deficits(roster, Map.of(1, 2), 2, 10);

		assertEquals(Map.of(2, 1), deficits);
	}

	@Test
	@DisplayName("cap holds the total at 'allowed', keeping tier priority")
	void cap_holdsTotal() {
		Map<Integer, Integer> deficits = new LinkedHashMap<>();
		deficits.put(1, 2);
		deficits.put(2, 3);

		Map<Integer, Integer> capped = RosterPlanner.cap(deficits, 3);

		assertEquals(Map.of(1, 2, 2, 1), capped);
	}

	@Test
	@DisplayName("cap returns everything when allowed covers the full total")
	void cap_passesThroughWhenRoomEnough() {
		Map<Integer, Integer> deficits = Map.of(1, 2, 2, 3);

		assertEquals(deficits, RosterPlanner.cap(deficits, 10));
	}

	@Test
	@DisplayName("cap is empty when allowed is zero or negative, or deficits is null")
	void cap_emptyWhenNothingAllowed() {
		Map<Integer, Integer> deficits = Map.of(1, 2);

		assertTrue(RosterPlanner.cap(deficits, 0).isEmpty());
		assertTrue(RosterPlanner.cap(deficits, -1).isEmpty());
		assertTrue(RosterPlanner.cap(null, 5).isEmpty());
	}

}
