package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * Cop backup-request tuning ({@code cops.yml}'s {@code Cops.Backup} block): when a cop goes down, the squad may
 * radio for extra cops on top of the wanted-level count.
 *
 * @param enabled     {@code false} turns backup requests off entirely.
 * @param extraCops   cops added on top of the wanted-level count, still capped by {@code Behaviour.Max_Per_Player}.
 * @param durationMs  how long the extra cops stay requested before the surplus (not fighting) walk home.
 * @param cooldownMs  minimum gap between two backup requests from one group.
 * @since 1.13.0
 */
public record BackupSettings(boolean enabled, int extraCops, long durationMs, long cooldownMs) {

	/** Enabled, +1 cop, 30s duration (600 ticks), 60s cooldown (1200 ticks) — matches the shipped cops.yml. */
	public static final BackupSettings DEFAULT = new BackupSettings(true, 1, 600 * 50L, 1200 * 50L);
}
