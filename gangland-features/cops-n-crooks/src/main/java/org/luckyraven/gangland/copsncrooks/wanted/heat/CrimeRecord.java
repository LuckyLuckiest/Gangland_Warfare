package org.luckyraven.gangland.copsncrooks.wanted.heat;

import org.bukkit.Location;

/**
 * One crime on a player's current chase: its id, the heat it was worth after multipliers, when (clock millis) and where.
 *
 * @since 0.15.0
 */
public record CrimeRecord(String crimeId, double heat, long at, Location location) {
}
