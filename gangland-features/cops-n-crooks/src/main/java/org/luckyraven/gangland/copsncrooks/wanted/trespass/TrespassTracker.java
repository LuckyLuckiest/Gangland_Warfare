package org.luckyraven.gangland.copsncrooks.wanted.trespass;

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

	/**
	 * Replaces the player's inside-set with {@code current} (region ids) and returns the ids that were not inside
	 * before: the entries. Never null.
	 */
	public Set<String> entered(UUID player, Set<String> current) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * True, and records {@code nowMillis}, when the player has not been charged for {@code region} within the last
	 * {@code repeatMillis}; false (nothing recorded) otherwise.
	 */
	public boolean claim(UUID player, String region, long nowMillis, long repeatMillis) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Forgets the player's inside-set (quit). The throttle times stay so a relog does not reset them. */
	public void clear(UUID player) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
