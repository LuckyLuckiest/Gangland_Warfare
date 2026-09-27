package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure arithmetic for mixed-squad rosters and backup waves (0.12 F4). No Bukkit/Citizens types, so it is fully
 * unit-tested off the main thread.
 */
public final class RosterPlanner {

	private RosterPlanner() {
	}

	/**
	 * The per-tier counts still missing from {@code currentByTier} to reach {@code roster}, in ascending tier order,
	 * with the total clamped so {@code currentTotal} plus the returned counts never exceeds {@code maxTotal}, nor the
	 * roster's own total. The roster-total clamp matters for the single-tier fallback roster
	 * ({@code CopSpawnManager#getRosterForWantedLevel}): without it, cops left over from an earlier, lower wanted
	 * level (and not covered by the current roster's tier at all) are never counted against the new roster, so each
	 * star rise stacks a whole new squad on top instead of reaching the roster's total the way the pre-0.12 single-tier
	 * target count did.
	 *
	 * @param roster the target roster, {@code tier -> count}; a {@code null} or empty roster yields no deficits
	 * @param currentByTier how many cops of each tier are currently assigned
	 * @param currentTotal the group's current total cop count (all tiers)
	 * @param maxTotal the hard cap on the group's total cop count ({@code Max_Per_Player})
	 *
	 * @return the deficit per tier, ascending tier order; empty when nothing is missing or no room remains
	 */
	public static Map<Integer, Integer> deficits(Map<Integer, Integer> roster, Map<Integer, Integer> currentByTier,
	                                             int currentTotal, int maxTotal) {
		Map<Integer, Integer> result = new LinkedHashMap<>();
		if (roster == null || roster.isEmpty()) return result;

		int rosterTotal = roster.values().stream().mapToInt(Integer::intValue).sum();
		int cap         = Math.min(maxTotal, rosterTotal);
		int room        = cap - currentTotal;
		if (room <= 0) return result;

		List<Integer> tiers = new ArrayList<>(roster.keySet());
		Collections.sort(tiers);

		for (int tier : tiers) {
			if (room <= 0) break;

			int target  = roster.getOrDefault(tier, 0);
			int current = currentByTier == null ? 0 : currentByTier.getOrDefault(tier, 0);
			int need    = target - current;
			if (need <= 0) continue;

			int take = Math.min(need, room);
			result.put(tier, take);
			room -= take;
		}

		return result;
	}

	/**
	 * Caps the total count across {@code deficits} at {@code allowed}, keeping tier order and giving earlier
	 * (lower-numbered) tiers priority. Used to hold back backup-wave respawns until their delay elapses: only the
	 * roster's organic growth (a rising wanted level) is allowed through immediately.
	 *
	 * @param deficits the full per-tier deficit, as returned by {@link #deficits}
	 * @param allowed the maximum total cops that may spawn this pass
	 *
	 * @return a per-tier count summing to at most {@code allowed}
	 */
	public static Map<Integer, Integer> cap(Map<Integer, Integer> deficits, int allowed) {
		Map<Integer, Integer> capped    = new LinkedHashMap<>();
		int                   remaining = allowed;
		if (remaining <= 0 || deficits == null) return capped;

		for (Map.Entry<Integer, Integer> entry : deficits.entrySet()) {
			if (remaining <= 0) break;

			int take = Math.min(entry.getValue(), remaining);
			if (take <= 0) continue;

			capped.put(entry.getKey(), take);
			remaining -= take;
		}

		return capped;
	}

}
