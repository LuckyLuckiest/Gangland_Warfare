package org.luckyraven.gangland.copsncrooks.wanted.config;

/**
 * {@code Wanted.Evasion.Quiet_Speed}: the search timer runs faster the longer the player stays quiet.
 *
 * @param enabled           {@code false} keeps the timer at its base speed.
 * @param perMinute         speed added per full quiet minute.
 * @param max               highest quiet speed.
 * @param backupSkipSeconds quiet seconds after which the next backup wave is skipped.
 * @since 0.16.0
 */
public record QuietSpeedSettings(boolean enabled, double perMinute, double max, int backupSkipSeconds) {

	/** The shipped {@code Wanted.Evasion.Quiet_Speed}. */
	public static final QuietSpeedSettings DEFAULT = new QuietSpeedSettings(true, 0.25, 2.0, 60);

	/** Timer speed after {@code quietMs} of quiet: 1 plus {@code perMinute} per full minute, capped at {@code max}. */
	public double speedFor(long quietMs) {
		if (!enabled) return 1.0;

		return Math.min(max, 1 + perMinute * (quietMs / 60_000L));
	}
}
