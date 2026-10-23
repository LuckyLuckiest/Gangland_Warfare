package org.luckyraven.gangland.copsncrooks.wanted.trespass;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per player: the restricted regions the player is inside (so only an entry commits, standing does not) and the time a
 * region last cost the player a {@code Trespass_Restricted} crime (so leaving and re-entering is throttled). Main thread
 * only.
 *
 * @since 0.20.0
 */
public final class TrespassTracker {

	private final Map<UUID, Set<String>>         inside  = new HashMap<>();
	private final Map<UUID, Map<String, Long>> charged = new HashMap<>();

	/**
	 * Replaces the player's inside-set with {@code current} (region ids) and returns the ids that were not inside
	 * before: the entries. Never null.
	 */
	public Set<String> entered(UUID player, Set<String> current) {
		Set<String> previous = inside.getOrDefault(player, Set.of());
		Set<String> now = new HashSet<>(current);

		if (now.isEmpty()) {
			inside.remove(player);
		} else {
			inside.put(player, now);
		}

		Set<String> entries = new HashSet<>(now);
		entries.removeAll(previous);
		return entries;
	}

	/**
	 * True, and records {@code nowMillis}, when the player has not been charged for {@code region} within the last
	 * {@code repeatMillis}; false (nothing recorded) otherwise.
	 */
	public boolean claim(UUID player, String region, long nowMillis, long repeatMillis) {
		Map<String, Long> times = charged.computeIfAbsent(player, k -> new HashMap<>());
		Long last = times.get(region);

		if (last != null && nowMillis - last < repeatMillis) {
			return false;
		}

		times.put(region, nowMillis);
		return true;
	}

	/** Forgets the player's inside-set (quit). The throttle times stay so a relog does not reset them. */
	public void clear(UUID player) {
		inside.remove(player);
	}
}
