package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.core.user.Level;

/**
 * Builds every gang's Level from gang/gang_rules.yml Progression (D2): Max_Level and Base_Experience, formula
 * base * level ^ 1.5 (level n needs Base_Experience x n^1.5 more XP than level n-1). Static because Gang builds its
 * Level in its constructor (same reason as GangSettings.bind). GangRules binds it on load; values change for gangs
 * loaded after a restart.
 */
public final class GangLevels {

	public static final int    DEFAULT_MAX_LEVEL       = 20;
	public static final double DEFAULT_BASE_EXPERIENCE = 200;
	public static final String FORMULA                 = "base * level ^ 1.5";

	private static volatile int    maxLevel       = DEFAULT_MAX_LEVEL;
	private static volatile double baseExperience = DEFAULT_BASE_EXPERIENCE;

	private GangLevels() {
	}

	/** max is raised to 1, base to 1 when lower. */
	public static void bind(int max, double base) {
		maxLevel       = Math.max(1, max);
		baseExperience = Math.max(1, base);
	}

	public static Level newLevel() {
		Level level = new Level(maxLevel, baseExperience);
		level.setFormula(FORMULA);
		return level;
	}

}
