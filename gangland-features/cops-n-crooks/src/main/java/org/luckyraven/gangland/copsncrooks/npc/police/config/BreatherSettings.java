package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.List;

/**
 * The pause after a squad is wiped before backup is dispatched ({@code cops.yml}'s {@code Cops.Breather}).
 *
 * @param enabled           {@code false} refills a wiped squad at once.
 * @param seconds           breather per wanted level, index 0 = 1 star; levels outside the list clamp to its ends.
 * @param wipeWindowSeconds a squad counts as wiped when it loses every cop within this many seconds.
 * @since 0.16.0
 */
public record BreatherSettings(boolean enabled, List<Integer> seconds, int wipeWindowSeconds) {

	/** Matches the shipped cops.yml: 15, 13, 10, 8, 6 s for 1..5 stars, wipe window 10 s. */
	public static final BreatherSettings DEFAULT = new BreatherSettings(true, List.of(15, 13, 10, 8, 6), 10);

	/** The DEFAULT numbers with the breather off. */
	public static final BreatherSettings DISABLED = new BreatherSettings(false, DEFAULT.seconds, DEFAULT.wipeWindowSeconds);

	public BreatherSettings {
		seconds = List.copyOf(seconds);
	}

	/** The breather in milliseconds for {@code level} (clamped into the list); 0 when disabled or the list is empty. */
	public long breatherMs(int level) {
		if (!enabled || seconds.isEmpty()) return 0L;
		int index = Math.max(0, Math.min(seconds.size() - 1, level - 1));
		return Math.max(0, seconds.get(index)) * 1000L;
	}
}
