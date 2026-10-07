package org.luckyraven.gangland.copsncrooks.wanted.learn;

import org.luckyraven.gangland.core.wanted.WantedCause;

import java.util.UUID;

/**
 * One finished chase as the learner sees it. Both durations already have offline time removed.
 *
 * @param contactMs time from the chase start to the last time the squad lost sight of the player.
 * @since 0.15.2
 */
public record ChaseRecord(UUID player, WantedCause startCause, WantedCause endCause, int peak, long chaseMs,
                          long contactMs, long endedAt) {
}
