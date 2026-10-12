package org.luckyraven.gangland.turf.listener;

import org.luckyraven.gangland.turf.config.TurfMessages;

import java.util.Map;

/**
 * Pure text for the capture boss bar: the time left at the current pace and the title line. Bukkit-free so the pace
 * rule can be tested on its own. One rule for every turf since 0.17.0: attackers against defenders plus guards.
 */
public final class BossBarText {

	private BossBarText() {
	}

	/**
	 * Seconds until the capture reaches 100 at the current pace, rounded up. The rate is {@code net * 100 / duration}
	 * per second, never above {@code 100 / minCaptureSeconds} when that is positive (a capture cannot finish sooner
	 * than its minimum time).
	 *
	 * @param progress          percent within the capture, 0..100
	 * @param net               attackers minus defenders minus guards (see {@link #pace})
	 * @param durationSeconds   base time for one attacker to fill 0..100 with no defenders
	 * @param minCaptureSeconds the shortest a capture can take, or 0 for no clamp
	 * @return seconds left, or -1 when the capture is stalled (net &lt;= 0)
	 */
	public static long secondsLeft(double progress, int net, int durationSeconds, int minCaptureSeconds) {
		if (net <= 0) {
			return -1L;
		}
		double rate = net * 100.0 / Math.max(1, durationSeconds);
		if (minCaptureSeconds > 0) {
			rate = Math.min(rate, 100.0 / minCaptureSeconds);
		}
		double remaining = Math.max(0.0, 100.0 - progress) / rate;
		return (long) Math.ceil(remaining);
	}

	/**
	 * The net rate the seconds-left formula runs on: attackers minus defenders minus the guards holding the bar. A
	 * plain tug of war, for owned and unclaimed turfs alike.
	 *
	 * @param attackers the challenger's count inside
	 * @param defenders the defending count inside
	 * @param guards    the guards counted on the turf
	 * @return the pace to pass to {@link #secondsLeft}
	 */
	public static int pace(int attackers, int defenders, int guards) {
		return attackers - defenders - guards;
	}

	/**
	 * The defenders number on the boss bar title: the count, with the guards appended when any hold the bar.
	 *
	 * @param text      the turf messages, for the guards suffix
	 * @param defenders the defending count inside
	 * @param guards    the guards counted on the turf
	 * @return e.g. {@code 1}, or {@code 1 +2 guards} (colour codes included)
	 */
	public static String defendersLabel(TurfMessages text, int defenders, int guards) {
		if (guards == 0) {
			return String.valueOf(defenders);
		}
		return defenders + text.text("Info.BossBar_Guards", " &7+%guards% guards", "guards", guards);
	}

	/**
	 * Defenders of an unclaimed turf: every gang inside except the challenger.
	 *
	 * @param inside       people inside per gang id
	 * @param challengerId the challenging gang id, or null
	 * @return the number of people inside who are not in the challenging gang
	 */
	public static int unclaimedDefenders(Map<Integer, Integer> inside, Integer challengerId) {
		return inside.entrySet()
				.stream()
				.filter(entry -> !entry.getKey().equals(challengerId))
				.mapToInt(Map.Entry::getValue)
				.sum();
	}

	/**
	 * The boss bar title with its five placeholders filled. time is already rendered (a duration or the stalled word).
	 */
	public static String title(TurfMessages text, String gang, String turf, int attackers, String defenders,
	                           String time) {
		return text.text("Info.BossBar_Title",
		                 "&c%gang% &fis capturing &e%turf% &7- &f%attackers% &7vs &f%defenders% &7- &f%time%",
		                 "gang", gang,
		                 "turf", turf,
		                 "attackers", attackers,
		                 "defenders", defenders,
		                 "time", time);
	}
}
