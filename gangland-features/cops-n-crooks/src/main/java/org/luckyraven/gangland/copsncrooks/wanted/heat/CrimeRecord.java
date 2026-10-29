package org.luckyraven.gangland.copsncrooks.wanted.heat;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * One crime on a player's current chase: its id, the heat it was worth after multipliers, when (clock millis) and where.
 *
 * @param kind      who reported it when it landed late (0.21.2; names the cause), null when it counted at once.
 * @param witnesses the event's witness count when it landed (0.21.2; the {@code %count%} of the cause line).
 * @since 0.15.0
 */
public record CrimeRecord(String crimeId, double heat, long at, Location location, @Nullable ReportKind kind,
                          int witnesses) {

	/** Pre-0.21.1 4-argument constructor, kept for existing callers: not reported, no witness. */
	public CrimeRecord(String crimeId, double heat, long at, Location location) {
		this(crimeId, heat, at, location, null, 0);
	}
}
