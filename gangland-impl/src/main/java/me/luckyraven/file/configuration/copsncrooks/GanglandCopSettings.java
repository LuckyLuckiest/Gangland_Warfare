package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.npc.police.config.CopSettings;
import me.luckyraven.file.configuration.Settings;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * {@link CopSettings} implementation backed by {@link Settings}.
 * <p>
 * When formula mode is enabled, the expression stored in {@code settings.yml} under {@code Cops.Count.Formula} is
 * evaluated using <a href="https://www.objecthunter.net/exp4j/">exp4j</a> with four variables: {@code level},
 * {@code base}, {@code perLevel}, {@code max}. The result is clamped to {@code [1, max]}. Any evaluation error falls
 * back to the linear formula.
 * <p>
 * When formula mode is disabled (the default), the linear formula {@code min(base + (level - 1) * perLevel, max)} is
 * used directly.
 */
public class GanglandCopSettings implements CopSettings {

	@Override
	public int getCountForLevel(int level) {
		int base     = Settings.getCopCountBase();
		int perLevel = Settings.getCopCountPerLevel();
		int max      = Settings.getCopCountMax();

		if (Settings.isCopCountFormulaEnabled()) {
			String formula = Settings.getCopCountFormula();

			if (formula != null && !formula.isBlank()) {
				Integer max1 = formulaCalculation(level, formula, base, perLevel, max);
				if (max1 != null) return max1;
			}
		}

		return Math.min(base + (level - 1) * perLevel, max);
	}

	@Override
	public int getMaxWantedLevel() {
		return Settings.getWantedMaximumLevel();
	}

	@Override
	public int getMaxCopsPerPlayer() {
		return Settings.getCopMaxPerPlayer();
	}

	@Override
	public int getAiTickRate() {
		return Settings.getCopAiTickRate();
	}

	@Override
	public int getSpawnCheckRate() {
		return Settings.getCopSpawnCheckRate();
	}

	@Override
	public double getCuffRadius() {
		return Settings.getCopCuffRadius();
	}

	@Override
	public int getMaxCuffAttempts() {
		return Settings.getCopMaxCuffAttempts();
	}

	@Override
	public int getCuffCooldownTicks() {
		return Settings.getCopCuffCooldownTicks();
	}

	@Override
	public double getAlertRange() {
		return Settings.getCopAlertRange();
	}

	@Override
	public double getCombatRange() {
		return Settings.getCopCombatRange();
	}

	@Override
	public int getAttackCooldownTicks() {
		return Settings.getCopAttackCooldownTicks();
	}

	@Override
	public double getMinSpawnDistance() {
		return Settings.getCopSpawnMinDistance();
	}

	@Override
	public double getMaxSpawnDistance() {
		return Settings.getCopSpawnMaxDistance();
	}

	@Override
	public double getPhase1MinDistance() {
		return Settings.getCopSpawnPhase1MinDistance();
	}

	@Override
	public double getSpawnRadiusShrinkStep() {
		return Settings.getCopSpawnRadiusShrinkStep();
	}

	@Override
	public int getVerticalSearchRange() {
		return Settings.getCopSpawnVerticalSearchRange();
	}

	@Override
	public int getSpawnYOffset() {
		return Settings.getCopSpawnYOffset();
	}

	@Override
	public int getMinOpenHorizontalSides() {
		return Settings.getCopSpawnMinOpenHorizontalSides();
	}

	@Override
	public double getSpawnerPreferenceRadius() {
		return Settings.getCopSpawnSpawnerPreferenceRadius();
	}

	@Override
	public double getVisibilityCheckDistance() {
		return Settings.getCopSpawnVisibilityCheckDistance();
	}

	@Override
	public int getSpawnPhase1Attempts() {
		return Settings.getCopSpawnPhase1Attempts();
	}

	@Override
	public int getSpawnPhase2Attempts() {
		return Settings.getCopSpawnPhase2Attempts();
	}

	@Override
	public int getNavigationRecalculationTicks() {
		return Settings.getNpcNavRecalculationTicks();
	}

	@Override
	public int getStuckCheckIntervalTicks() {
		return Settings.getNpcNavStuckCheckInterval();
	}

	@Override
	public int getMaxStuckChecks() {
		return Settings.getNpcNavMaxStuckChecks();
	}

	@Override
	public int getMaxHopelessStuckChecks() {
		return Settings.getNpcNavMaxHopelessStuckChecks();
	}

	@Override
	public double getHopelessCloseThreshold() {
		return Settings.getNpcNavHopelessCloseThreshold();
	}

	@Override
	public double getMinProgressDistance() {
		return Settings.getNpcNavMinProgressDistance();
	}

	@Override
	public double getRangedMinDistance() {
		return Settings.getNpcNavRangedMinDistance();
	}

	@Override
	public double getRangedMaxDistance() {
		return Settings.getNpcNavRangedMaxDistance();
	}

	@Override
	public int getMinRepathAfterLossTicks() {
		return Settings.getNpcNavMinRepathAfterLossTicks();
	}

	@Override
	public double getPursuitMaxDistance() {
		return Settings.getCopPursuitMaxDistance();
	}

	@Override
	public int getPursuitMaxTicks() {
		return Settings.getCopPursuitMaxTicks();
	}

	@Override
	public int getMaxReturnTicks() {
		return Settings.getCopReturnMaxTicks();
	}

	@Override
	public double getStationArrivalDistance() {
		return Settings.getCopReturnStationArrivalDistance();
	}

	@Override
	public int getStartingAmmoMagazines() {
		return Settings.getCopStartingAmmoMagazines();
	}

	@Override
	public double getGuardRadius() {
		return Settings.getDetainmentGuardRadius();
	}

	private Integer formulaCalculation(int level, String formula, int base, int perLevel, int max) {
		try {
			double result = new ExpressionBuilder(formula).variables("level", "base", "perLevel", "max")
			                                              .build()
			                                              .setVariable("level", level)
			                                              .setVariable("base", base)
			                                              .setVariable("perLevel", perLevel)
			                                              .setVariable("max", max)
			                                              .evaluate();

			return Math.max(1, Math.min(max, (int) Math.round(result)));
		} catch (Exception ignored) {
			// fall through to linear formula
		}
		return null;
	}
}
