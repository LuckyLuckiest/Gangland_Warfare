package org.luckyraven.gangland.copsncrooks.wanted.learn;

import org.luckyraven.gangland.core.wanted.WantedCause;

import java.util.UUID;

/**
 * One finished chase, as {@link ChaseLearner#record} reads it. Times already have the player's offline time removed.
 *
 * @param player     who was chased.
 * @param startCause what started the chase; only {@link WantedCause#CRIME} is learned from.
 * @param endCause   what ended it.
 * @param peak       the highest level the chase reached.
 * @param chaseMs    from the chase start to its end.
 * @param contactMs  from the chase start to the last time the squad lost sight of him.
 * @param endedAt    wall-clock millis of the end.
 * @since 0.15.2
 */
public record ChaseRecord(UUID player, WantedCause startCause, WantedCause endCause, int peak, long chaseMs,
                          long contactMs, long endedAt) {
}
