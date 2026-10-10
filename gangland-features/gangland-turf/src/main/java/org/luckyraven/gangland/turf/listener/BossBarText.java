package org.luckyraven.gangland.turf.listener;

import org.luckyraven.gangland.turf.config.TurfMessages;

import java.util.Map;

/**
 * Pure text for the capture boss bar: the time left at the current pace and the title line. Bukkit-free so the pace
 * rule can be tested on its own. W2 replaces the pace rule (guards, plurality); keep this small.
 */
public final class BossBarText {

	private BossBarText() {
	}

	/**
	 * Seconds until the capture reaches 100 at the current pace, rounded up. For an unclaimed turf this is the time
	 * left in the current phase (CLAIM or CONSOLIDATE), not the whole capture.
	 *
	 * @param progress        percent within the current phase, 0..100
	 * @param net             attackers minus defenders
	 * @param durationSeconds base time for one attacker to fill 0..100 with no defenders
	 * @return seconds left, or -1 when the capture is stalled (net &lt;= 0)
	 */
	public static long secondsLeft(double progress, int net, int durationSeconds) {
		if (net <= 0) {
			return -1L;
		}
		double remaining = Math.max(0.0, 100.0 - progress) * durationSeconds / (net * 100.0);
		return (long) Math.ceil(remaining);
	}

	/**
	 * The net rate the seconds-left formula runs on. The unclaimed CLAIM phase is global (anyone inside fills it at one
	 * attacker's rate), so its pace is 1 whenever anyone is inside and 0 otherwise. Every other phase is attackers minus
	 * defenders.
	 *
	 * @param globalClaimPhase true for an unclaimed turf in the CLAIM phase
	 * @param attackers        the challenger's count inside
	 * @param defenders        the defending count inside
	 * @return the pace to pass to {@link #secondsLeft}
	 */
	public static int pace(boolean globalClaimPhase, int attackers, int defenders) {
		if (globalClaimPhase) {
			return attackers + defenders > 0 ? 1 : 0;
		}
		return attackers - defenders;
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
	public static String title(TurfMessages text, String gang, String turf, int attackers, int defenders, String time) {
		return text.text("Info.BossBar_Title",
		                 "&c%gang% &fis capturing &e%turf% &7- &f%attackers% &7vs &f%defenders% &7- &f%time%",
		                 "gang", gang,
		                 "turf", turf,
		                 "attackers", attackers,
		                 "defenders", defenders,
		                 "time", time);
	}
}
