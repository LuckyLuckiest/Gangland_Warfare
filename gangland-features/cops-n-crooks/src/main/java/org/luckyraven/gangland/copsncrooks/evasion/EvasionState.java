package org.luckyraven.gangland.copsncrooks.evasion;

/**
 * Where a wanted player stands in the line-of-sight evasion loop (0.12).
 */
public enum EvasionState {

	/**
	 * The cop squad has a fresh sighting: stars are solid and the evasion clock is paused and reset.
	 */
	SEEN,

	/**
	 * Nobody has seen the player for {@code Lost_Sight_Seconds}: a search zone is open on the last known position and
	 * the evasion clock counts toward dropping a star.
	 */
	SEARCHING,

	/**
	 * Evasion does not drive this player (not wanted, evasion disabled, or no cop was ever sent after the player, in
	 * which case the old repeating decay timer is the fallback).
	 */
	NONE
}
