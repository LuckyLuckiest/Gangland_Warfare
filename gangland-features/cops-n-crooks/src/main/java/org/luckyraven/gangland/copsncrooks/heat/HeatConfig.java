package org.luckyraven.gangland.copsncrooks.heat;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.keystone.util.NumberUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable view of the {@code Heat:} section of {@code npc/cops.yml} (0.12 heat ledger).
 *
 * <p>Every key is optional: a missing section yields {@link #defaults()}, a missing key its default below, so a
 * server running an older {@code cops.yml} keeps loading.
 * <ul>
 *     <li>{@code Enable} ({@code true}) — when {@code false} kills score through the pre-0.12 flat increment.</li>
 *     <li>{@code Star_Thresholds} ({@code [100, 250, 450, 700, 1000]}) — heat needed for each star. A list shorter
 *     than the wanted maximum level is stretched with {@link NumberUtil#resizeLinear}, like
 *     {@code Wanted.Kill_Combo.Kill_Counter}.</li>
 *     <li>{@code Streak_Bonus} ({@code 1.5}) — multiplier for a crime committed while the kill combo is running.</li>
 *     <li>{@code Turf_War_Multiplier} ({@code 0.5}) — multiplier for a kill whose victim stands inside a contested
 *     turf.</li>
 *     <li>{@code Assault_Cop_Cooldown_Seconds} ({@code 10}) — one {@code Assault_Cop} per player and cop per
 *     window.</li>
 *     <li>{@code Crimes.<key>} — the heat weight of each {@link Crime}.</li>
 * </ul>
 */
@Getter
public final class HeatConfig {

	public static final List<Integer> DEFAULT_STAR_THRESHOLDS           = List.of(100, 250, 450, 700, 1000);
	public static final double        DEFAULT_STREAK_BONUS              = 1.5;
	public static final double        DEFAULT_TURF_WAR_MULTIPLIER       = 0.5;
	public static final int           DEFAULT_ASSAULT_COP_COOLDOWN_SECS = 10;

	private final boolean             enabled;
	private final List<Integer>       starThresholds;
	private final double              streakBonus;
	private final double              turfWarMultiplier;
	private final int                 assaultCopCooldownSeconds;
	private final Map<Crime, Integer> weights;

	public HeatConfig(boolean enabled, @Nullable List<Integer> starThresholds, double streakBonus,
	                  double turfWarMultiplier, int assaultCopCooldownSeconds, @Nullable Map<Crime, Integer> weights) {
		this.enabled                   = enabled;
		this.starThresholds            = sanitizeThresholds(starThresholds);
		this.streakBonus               = Math.max(0, streakBonus);
		this.turfWarMultiplier         = Math.max(0, turfWarMultiplier);
		this.assaultCopCooldownSeconds = Math.max(0, assaultCopCooldownSeconds);

		Map<Crime, Integer> resolved = new EnumMap<>(Crime.class);
		for (Crime crime : Crime.values()) {
			Integer weight = weights == null ? null : weights.get(crime);
			resolved.put(crime, weight == null ? crime.getDefaultWeight() : Math.max(0, weight));
		}
		this.weights = Collections.unmodifiableMap(resolved);
	}

	/**
	 * @return the shipped defaults, used when {@code cops.yml} has no {@code Heat:} section
	 */
	public static HeatConfig defaults() {
		return new HeatConfig(true, DEFAULT_STAR_THRESHOLDS, DEFAULT_STREAK_BONUS, DEFAULT_TURF_WAR_MULTIPLIER,
		                      DEFAULT_ASSAULT_COP_COOLDOWN_SECS, null);
	}

	/**
	 * Parses the {@code Heat:} section of the {@code cops.yml} root.
	 *
	 * @param root positional reader over the {@code cops.yml} root mapping
	 * @param report issue collector drained by the enclosing loader
	 *
	 * @return the parsed config; {@link #defaults()} when the section is absent
	 */
	public static HeatConfig parse(@Nullable NodeReader root, ConfigReport report) {
		if (root == null) return defaults();

		MappingNode section = root.get("Heat").asMapping().orNull();
		if (section == null) return defaults();

		NodeReader heat = NodeReader.of(section, report);

		boolean       enabled    = heat.get("Enable").asBool().orDefault(true);
		List<Integer> thresholds = heat.get("Star_Thresholds").asList().ofInts().orEmpty();
		double        streak     = heat.get("Streak_Bonus").asDouble().min(0).orDefault(DEFAULT_STREAK_BONUS);
		double        turfWar    = heat.get("Turf_War_Multiplier").asDouble().min(0)
		                               .orDefault(DEFAULT_TURF_WAR_MULTIPLIER);
		int           cooldown   = heat.get("Assault_Cop_Cooldown_Seconds").asInt().min(0)
		                               .orDefault(DEFAULT_ASSAULT_COP_COOLDOWN_SECS);

		Map<Crime, Integer> weights = new EnumMap<>(Crime.class);
		MappingNode         crimes  = heat.get("Crimes").asMapping().orNull();
		if (crimes != null) {
			NodeReader crimeReader = NodeReader.of(crimes, report);
			for (Crime crime : Crime.values()) {
				weights.put(crime, crimeReader.get(crime.getConfigKey()).asInt().min(0)
				                              .orDefault(crime.getDefaultWeight()));
			}
		}

		return new HeatConfig(enabled, thresholds, streak, turfWar, cooldown, weights);
	}

	/**
	 * @return the heat weight of {@code crime}
	 */
	public int weightOf(Crime crime) {
		Integer weight = weights.get(crime);
		return weight == null ? crime.getDefaultWeight() : weight;
	}

	/**
	 * The star level a heat total reaches: the number of thresholds at or below {@code heat}, capped at
	 * {@code maxLevel}.
	 *
	 * @param heat the player's heat
	 * @param maxLevel the wanted maximum level
	 *
	 * @return the star level, {@code 0..maxLevel}
	 */
	public int starsFor(int heat, int maxLevel) {
		if (maxLevel <= 0) return 0;

		List<Integer> thresholds = thresholdsFor(maxLevel);

		int stars = 0;
		for (int threshold : thresholds) {
			if (threshold <= heat) stars++;
		}

		return Math.min(stars, maxLevel);
	}

	/**
	 * The heat a player holding {@code level} stars has at least: the threshold of that star. Used as the floor heat
	 * builds on, so stars gained outside the ledger (relog, {@code /wanted add}) and stars lost by evasion keep heat
	 * consistent with the level.
	 *
	 * @param level the star level
	 *
	 * @return {@code 0} for {@code level <= 0}, otherwise the threshold of that star (clamped to the last one)
	 */
	public int floorFor(int level) {
		return floorFor(level, starThresholds.size());
	}

	/**
	 * {@link #floorFor(int)} against the thresholds stretched to {@code maxLevel} entries, matching
	 * {@link #starsFor(int, int)}.
	 */
	public int floorFor(int level, int maxLevel) {
		if (level <= 0) return 0;

		List<Integer> thresholds = thresholdsFor(Math.max(1, maxLevel));
		int           index      = Math.min(level, thresholds.size()) - 1;

		return thresholds.get(index);
	}

	private List<Integer> thresholdsFor(int maxLevel) {
		if (starThresholds.size() >= maxLevel) return starThresholds;
		// create a linear list of thresholds if not enough thresholds are configured
		return NumberUtil.resizeLinear(starThresholds, maxLevel);
	}

	private static List<Integer> sanitizeThresholds(@Nullable List<Integer> thresholds) {
		if (thresholds == null || thresholds.isEmpty()) return DEFAULT_STAR_THRESHOLDS;

		List<Integer> sorted = new ArrayList<>();
		for (Integer threshold : thresholds) {
			if (threshold != null) sorted.add(Math.max(1, threshold));
		}
		if (sorted.isEmpty()) return DEFAULT_STAR_THRESHOLDS;

		Collections.sort(sorted);
		return Collections.unmodifiableList(sorted);
	}

}
