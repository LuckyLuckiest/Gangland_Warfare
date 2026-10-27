package org.luckyraven.gangland.copsncrooks.wanted.heat;

import org.bukkit.Location;

/**
 * One crime on a player's current chase: its id, the heat it was worth after multipliers, when (clock millis) and where.
 *
 * @param reported a witness (not a cop) reported it: a landed 911 call (0.21.1, names the cause).
 * @since 0.15.0
 */
public record CrimeRecord(String crimeId, double heat, long at, Location location, boolean reported) {

	/** Pre-0.21.1 4-argument constructor, kept for existing callers: not reported. */
	public CrimeRecord(String crimeId, double heat, long at, Location location) {
		this(crimeId, heat, at, location, false);
	}
}
