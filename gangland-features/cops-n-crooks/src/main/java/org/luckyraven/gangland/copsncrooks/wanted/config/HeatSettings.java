package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.luckyraven.keystone.util.NumberUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code Wanted.Heat} of {@code npc/wanted.yml}: how crimes add up to stars.
 *
 * @param enabled              {@code false} keeps today's behaviour.
 * @param starThresholds       heat needed for star 1, 2, ...
 * @param streakBonus          multiplier for crimes chained inside the kill-combo reset window.
 * @param seenByCopMultiplier  multiplier when a cop saw the crime.
 * @param turfWarMultiplier    multiplier inside a contested turf.
 * @param assaultRepeatSeconds how often the same assault counts again.
 * @param crimeWeights         heat per crime id.
 * @since 0.15.0
 */
public record HeatSettings(boolean enabled, List<Integer> starThresholds, double streakBonus,
                           double seenByCopMultiplier, double turfWarMultiplier, int assaultRepeatSeconds,
                           Map<String, Integer> crimeWeights) {
	/** The shipped {@code Wanted.Heat}. */
	public static final HeatSettings DEFAULT = new HeatSettings(true, List.of(100, 250, 450, 700, 1000), 1.5, 1.5, 0.5,
	                                                            10, defaultCrimes());

	private static Map<String, Integer> defaultCrimes() {
		Map<String, Integer> crimes = new LinkedHashMap<>();
		crimes.put("Brandish_Near_Cop", 25);
		crimes.put("Assault_Civilian", 30);
		crimes.put("Car_Theft", 60);
		crimes.put("Kill_Player", 80);
		crimes.put("Kill_Civilian", 100);
		crimes.put("Assault_Cop", 100);
		crimes.put("Resisting_Arrest", 100);
		crimes.put("Kill_Cop", 150);
		crimes.put("Safe_Cracking", 150);
		crimes.put("Store_Robbery", 200);
		crimes.put("Trespass_Restricted", 300);
		crimes.put("Jailbreak", 450);
		return Map.copyOf(crimes);
	}


	/** The heat weight of {@code crimeId} (exact, case-sensitive), 0 when it has none. */
	public int weightOf(String crimeId) {
		return crimeWeights.getOrDefault(crimeId, 0);
	}

	/** Stars {@code heat} is worth: how many of the first {@code maxLevel} thresholds it reaches. */
	public int starsFor(double heat, int maxLevel) {
		List<Integer> thresholds = thresholdsFor(maxLevel);
		int           stars      = 0;

		for (int i = 0; i < Math.min(maxLevel, thresholds.size()); i++) {
			if (heat >= thresholds.get(i)) stars = i + 1;
		}

		return stars;
	}

	/** The least heat a player at {@code level} stars keeps: 0 at no stars, else that star's threshold. */
	public double floorOf(int level, int maxLevel) {
		if (level <= 0) return 0;

		List<Integer> thresholds = thresholdsFor(maxLevel);
		if (thresholds.isEmpty()) return 0;

		return thresholds.get(Math.min(Math.min(level, maxLevel), thresholds.size()) - 1);
	}

	// A short list is stretched the way KillCombo stretches its kill counts.
	private List<Integer> thresholdsFor(int maxLevel) {
		if (starThresholds.isEmpty() || maxLevel <= 0 || starThresholds.size() >= maxLevel) return starThresholds;

		return NumberUtil.resizeLinear(starThresholds, maxLevel);
	}
}
