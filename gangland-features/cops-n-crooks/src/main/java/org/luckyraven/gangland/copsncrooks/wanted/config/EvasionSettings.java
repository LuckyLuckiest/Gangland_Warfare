package org.luckyraven.gangland.copsncrooks.wanted.config;

import java.util.List;

/**
 * {@code Wanted.Evasion} of {@code copsncrooks/wanted.yml}: losing the cops drops stars instead of the fixed decay timer.
 *
 * @param enabled          {@code false} keeps today's decay.
 * @param lostSightSeconds seconds without a cop seeing you before the search starts.
 * @param dropMode         what a completed evasion takes.
 * @param searchRadius     the search zone radius per wanted level.
 * @param secondsToDrop    seconds to evade per wanted level.
 * @param outsideZoneSpeed how much faster the timer runs while outside the zone.
 * @param auto             {@code Auto} block, read only while {@code dropMode} is AUTO.
 * @param hideout          the search timer's speed inside a hideout.
 * @param quietSpeed       the speed-up for staying quiet.
 * @param maxSpeed         cap on zone x hideout x quiet speed.
 * @since 0.15.0
 */
public record EvasionSettings(boolean enabled, int lostSightSeconds, DropMode dropMode, List<Integer> searchRadius,
                              List<Integer> secondsToDrop, double outsideZoneSpeed, AutoSettings auto, HideoutSettings hideout,
                              QuietSpeedSettings quietSpeed, double maxSpeed) {
	/** The shipped {@code Wanted.Evasion}. */
	public static final EvasionSettings DEFAULT = new EvasionSettings(true, 3, DropMode.ONE_STAR,
	                                                                  List.of(40, 60, 90, 130, 180),
	                                                                  List.of(10, 20, 30, 45, 60), 2.0,
	                                                                  AutoSettings.DEFAULT, HideoutSettings.DEFAULT,
	                                                                  QuietSpeedSettings.DEFAULT, 4.0);

	/** The 0.15.2 shape, with the default hideout, quiet-speed and cap. */
	public EvasionSettings(boolean enabled, int lostSightSeconds, DropMode dropMode, List<Integer> searchRadius,
	                       List<Integer> secondsToDrop, double outsideZoneSpeed, AutoSettings auto) {
		this(enabled, lostSightSeconds, dropMode, searchRadius, secondsToDrop, outsideZoneSpeed, auto,
		     HideoutSettings.DEFAULT, QuietSpeedSettings.DEFAULT, 4.0);
	}

	/** The 0.15.1 shape, with the default {@code Auto} block. */
	public EvasionSettings(boolean enabled, int lostSightSeconds, DropMode dropMode, List<Integer> searchRadius,
	                       List<Integer> secondsToDrop, double outsideZoneSpeed) {
		this(enabled, lostSightSeconds, dropMode, searchRadius, secondsToDrop, outsideZoneSpeed, AutoSettings.DEFAULT);
	}


	/** Search radius at {@code level}, the level clamped into the list. */
	public int radiusFor(int level) {
		return at(searchRadius, level);
	}

	/** Seconds to drop at {@code level}, the level clamped into the list. */
	public int secondsToDropFor(int level) {
		return at(secondsToDrop, level);
	}

	private static int at(List<Integer> list, int level) {
		return list.get(Math.max(1, Math.min(level, list.size())) - 1);
	}
}
