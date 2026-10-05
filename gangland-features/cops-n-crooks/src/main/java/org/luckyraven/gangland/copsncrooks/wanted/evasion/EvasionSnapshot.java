package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.events.wanted.EvasionState;

/**
 * What the evasion clock currently knows about one player; {@code secondsLeft} is 0 unless SEARCHING and
 * {@code zoneCentre} is non-null in SEARCHING and EVADED.
 *
 * @since 0.15.0
 */
public record EvasionSnapshot(EvasionState state, int level, int secondsLeft, @Nullable Location zoneCentre,
                              double zoneRadius) {
}
