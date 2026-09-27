package org.luckyraven.gangland.copsncrooks.evasion;

import org.luckyraven.gangland.file.configuration.Settings;

import java.util.List;
import java.util.Objects;

/**
 * Immutable view of {@code settings.yml} {@code Wanted.Evasion} (0.12). Values are normalized on construction: empty
 * per-star lists fall back to the shipped defaults, and every per-star value is at least 1.
 *
 * @param enabled          whether evasion drives chases ({@code Enable})
 * @param lostSightSeconds seconds without a squad sighting before the search starts ({@code Lost_Sight_Seconds})
 * @param dropMode         what the clock takes when it runs out ({@code Drop_Mode})
 * @param searchRadius     search zone radius in blocks, per star ({@code Search_Radius})
 * @param secondsToDrop    unseen seconds needed to drop a star, per star ({@code Seconds_To_Drop})
 * @param outsideZoneSpeed clock speed while the player is outside the zone ({@code Outside_Zone_Speed})
 * @param hideoutSpeed     reserved for 0.14 hideouts, parsed but unused ({@code Hideout_Speed})
 */
public record EvasionConfig(boolean enabled, int lostSightSeconds, EvasionDropMode dropMode, List<Integer> searchRadius,
                            List<Integer> secondsToDrop, double outsideZoneSpeed, double hideoutSpeed) {

	public static final List<Integer> DEFAULT_SEARCH_RADIUS   = List.of(40, 60, 90, 130, 180);
	public static final List<Integer> DEFAULT_SECONDS_TO_DROP = List.of(10, 20, 30, 45, 60);
	public static final int           DEFAULT_LOST_SIGHT      = 3;
	public static final double        DEFAULT_OUTSIDE_SPEED   = 2.0;
	public static final double        DEFAULT_HIDEOUT_SPEED   = 1.5;

	public EvasionConfig {
		lostSightSeconds = Math.max(1, lostSightSeconds);
		dropMode         = dropMode == null ? EvasionDropMode.ONE_STAR : dropMode;
		searchRadius     = normalize(searchRadius, DEFAULT_SEARCH_RADIUS);
		secondsToDrop    = normalize(secondsToDrop, DEFAULT_SECONDS_TO_DROP);
		outsideZoneSpeed = Math.max(0D, outsideZoneSpeed);
		hideoutSpeed     = Math.max(0D, hideoutSpeed);
	}

	/**
	 * The shipped values, evasion enabled.
	 */
	public static EvasionConfig defaults() {
		return new EvasionConfig(true, DEFAULT_LOST_SIGHT, EvasionDropMode.ONE_STAR, DEFAULT_SEARCH_RADIUS,
		                         DEFAULT_SECONDS_TO_DROP, DEFAULT_OUTSIDE_SPEED, DEFAULT_HIDEOUT_SPEED);
	}

	/**
	 * Reads the current {@link Settings} values. Cheap enough to call every tick, which keeps {@code /glw reload}
	 * effective without a reload hook.
	 */
	public static EvasionConfig fromSettings() {
		return new EvasionConfig(Settings.isWantedEvasionEnabled(), Settings.getWantedEvasionLostSightSeconds(),
		                         EvasionDropMode.parse(Settings.getWantedEvasionDropMode()),
		                         Settings.getWantedEvasionSearchRadius(), Settings.getWantedEvasionSecondsToDrop(),
		                         Settings.getWantedEvasionOutsideZoneSpeed(), Settings.getWantedEvasionHideoutSpeed());
	}

	/**
	 * The search zone radius at the given wanted level (index {@code level - 1}, clamped to the list).
	 *
	 * @param level the wanted level
	 *
	 * @return the radius in blocks, at least 1
	 */
	public int radiusFor(int level) {
		return valueFor(searchRadius, level);
	}

	/**
	 * The unseen seconds needed to drop a star at the given wanted level (index {@code level - 1}, clamped to the
	 * list).
	 *
	 * @param level the wanted level
	 *
	 * @return the seconds, at least 1
	 */
	public int secondsToDropFor(int level) {
		return valueFor(secondsToDrop, level);
	}

	private static int valueFor(List<Integer> values, int level) {
		int index = Math.max(0, Math.min(level - 1, values.size() - 1));
		return values.get(index);
	}

	private static List<Integer> normalize(List<Integer> values, List<Integer> defaults) {
		if (values == null || values.isEmpty()) return defaults;

		List<Integer> normalized = values.stream().filter(Objects::nonNull).map(value -> Math.max(1, value)).toList();
		return normalized.isEmpty() ? defaults : normalized;
	}
}
