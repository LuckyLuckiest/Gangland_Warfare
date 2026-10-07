package org.luckyraven.gangland.copsncrooks.wanted.learn;

/**
 * What the server has learned about chases peaking at one level, one {@code chase_level_stat} row.
 *
 * @param level          the chase peak level.
 * @param n              decayed, fair-share weighted chase count (at most 100).
 * @param escaped        decayed, weighted sum of outcomes.
 * @param typicalSeconds running median of the contact time of getaways, seconds.
 * @param typicalCount   weighted samples behind it, capped at 100.
 * @param updatedAt      millis of the last update.
 * @since 0.15.2
 */
public record ChaseLevelStat(int level, double n, double escaped, double typicalSeconds, double typicalCount,
                             long updatedAt) {
}
