package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * Squad regroup tuning ({@code cops.yml}'s {@code Cops.Regroup} block): a squad that loses {@code casualties} cops
 * within {@code windowMs} falls back for up to {@code fallBackMs} until backup arrives.
 *
 * @param enabled       {@code false} turns regrouping off.
 * @param casualties    cops lost inside the window that trigger a regroup.
 * @param windowMs      how recent a casualty must be to count.
 * @param fallBackMs    longest a regroup falls back.
 * @param cooldownMs    minimum gap between two regroups of one group.
 * @param arrivalRadius how close backup must be (blocks) to end the fall back.
 * @since 0.15.0
 */
public record RegroupSettings(boolean enabled, int casualties, long windowMs, long fallBackMs, long cooldownMs,
                              double arrivalRadius) {

	/** Matches the shipped cops.yml: 2 casualties in 20 s, fall back 15 s, one regroup per 60 s, arrival 24 blocks. */
	public static final RegroupSettings DEFAULT = new RegroupSettings(true, 2, 20_000L, 15_000L, 60_000L, 24.0);
}
