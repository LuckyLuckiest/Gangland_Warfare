package org.luckyraven.gangland.copsncrooks.detainment.wanted;

import org.luckyraven.gangland.core.wanted.WantedCause;

import java.util.UUID;

/**
 * Thin contract letting detainment flows (in cops-n-crooks) zero a player's wanted level without importing
 * {@code UserManager} / {@code Wanted} directly.
 */
public interface WantedClearContract {

	/**
	 * Returns the player's current wanted level so callers can snapshot it before clearing (used to price bail / bribe
	 * / sentence).
	 *
	 * @param playerId the player's UUID
	 *
	 * @return the current wanted level, or 0 if the user is not tracked
	 */
	int getWantedLevel(UUID playerId);

	/**
	 * Zeroes the player's wanted level. No-op if the user is not tracked.
	 */
	void clearWanted(UUID playerId);

	/**
	 * Zeroes the player's wanted level and tags the change with {@code cause} (it reaches the wanted events). No-op if
	 * the user is not tracked. Implementations override it; the default keeps older implementations compiling.
	 */
	default void clearWanted(UUID playerId, WantedCause cause) {
		clearWanted(playerId);
	}
}
