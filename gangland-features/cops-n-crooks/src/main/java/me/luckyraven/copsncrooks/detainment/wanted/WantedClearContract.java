package me.luckyraven.copsncrooks.detainment.wanted;

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
}
