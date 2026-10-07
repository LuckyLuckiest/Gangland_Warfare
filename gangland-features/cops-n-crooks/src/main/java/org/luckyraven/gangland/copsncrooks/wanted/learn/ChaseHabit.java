package org.luckyraven.gangland.copsncrooks.wanted.learn;

import java.util.UUID;

/**
 * A player's learned escape habit, one {@code chase_habit} row. Sums are decayed by {@code Decay_Per_Chase} per chase.
 *
 * @param player   the player.
 * @param n        decayed count of counted chases (at most 10 at decay 0.90).
 * @param actual   decayed sum of outcomes (1 escaped, 0.5 decayed, 0 caught).
 * @param expected decayed sum of the server's escape rate for each of those chases.
 * @param lastAt   millis of his last counted chase.
 * @since 0.15.2
 */
public record ChaseHabit(UUID player, double n, double actual, double expected, long lastAt) {
}
