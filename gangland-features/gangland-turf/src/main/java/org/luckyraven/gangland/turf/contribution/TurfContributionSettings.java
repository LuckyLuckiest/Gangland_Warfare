package org.luckyraven.gangland.turf.contribution;

import org.luckyraven.gangland.gang.member.Member;

/**
 * Per-action contribution-point values awarded during turf activity. Loaded from {@code turf/turf_settings.yml} under
 * {@code Contribution.Points.*} and wired in as a bean by {@code TurfModuleConfig}.
 *
 * <p>All awards go straight into {@link Member#increaseContribution(double)} — the
 * field already persists + already drives whatever contribution-based payout logic the gang module adds later.
 *
 * @param defenderPresenceTick points per 1-Hz tick to each online defender-gang member standing inside a turf that is
 * 		being contested (they're working to keep it)
 * @param attackerPresenceTick points per 1-Hz tick to each online attacker-gang member standing inside the turf they
 * 		are currently capturing (they're working to take it)
 * @param captureCompleteBonus flat one-shot bonus to every challenger-gang member inside the turf region at the
 * 		moment a capture completes
 * @param defenseSuccessBonus flat one-shot bonus to every defender-gang member inside the turf region at the moment a
 * 		capture is pushed back (capture-failed = DEFENDED)
 */
public record TurfContributionSettings(double defenderPresenceTick,
                                       double attackerPresenceTick,
                                       double captureCompleteBonus,
                                       double defenseSuccessBonus) {
}
