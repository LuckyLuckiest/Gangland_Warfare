package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.police.config.CopSettings;
import me.luckyraven.file.configuration.SettingAddon;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * {@link CopSettings} implementation backed by {@link SettingAddon}.
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
		int base     = SettingAddon.getCopCountBase();
		int perLevel = SettingAddon.getCopCountPerLevel();
		int max      = SettingAddon.getCopCountMax();

		if (SettingAddon.isCopCountFormulaEnabled()) {
			String formula = SettingAddon.getCopCountFormula();

			if (formula != null && !formula.isBlank()) {
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
			}
		}

		return Math.min(base + (level - 1) * perLevel, max);
	}

	@Override
	public int getMaxWantedLevel() {
		return SettingAddon.getWantedMaximumLevel();
	}

	@Override
	public int getMaxCopsPerPlayer() {
		return SettingAddon.getCopMaxPerPlayer();
	}

	@Override
	public int getAiTickRate() {
		return SettingAddon.getCopAiTickRate();
	}

	@Override
	public int getSpawnCheckRate() {
		return SettingAddon.getCopSpawnCheckRate();
	}

	@Override
	public double getCuffRadius() {
		return SettingAddon.getCopCuffRadius();
	}

	@Override
	public int getMaxCuffAttempts() {
		return SettingAddon.getCopMaxCuffAttempts();
	}

	@Override
	public int getCuffCooldownTicks() {
		return SettingAddon.getCopCuffCooldownTicks();
	}

	@Override
	public double getAlertRange() {
		return SettingAddon.getCopAlertRange();
	}

	@Override
	public double getCombatRange() {
		return SettingAddon.getCopCombatRange();
	}

	@Override
	public int getAttackCooldownTicks() {
		return SettingAddon.getCopAttackCooldownTicks();
	}

	@Override
	public double getMinSpawnDistance() {
		return SettingAddon.getCopSpawnMinDistance();
	}

	@Override
	public double getMaxSpawnDistance() {
		return SettingAddon.getCopSpawnMaxDistance();
	}

	@Override
	public double getPhase1MinDistance() {
		return SettingAddon.getCopSpawnPhase1MinDistance();
	}

	@Override
	public double getSpawnRadiusShrinkStep() {
		return SettingAddon.getCopSpawnRadiusShrinkStep();
	}

	@Override
	public int getVerticalSearchRange() {
		return SettingAddon.getCopSpawnVerticalSearchRange();
	}

	@Override
	public int getSpawnYOffset() {
		return SettingAddon.getCopSpawnYOffset();
	}

	@Override
	public int getMinOpenHorizontalSides() {
		return SettingAddon.getCopSpawnMinOpenHorizontalSides();
	}

	@Override
	public double getSpawnerPreferenceRadius() {
		return SettingAddon.getCopSpawnSpawnerPreferenceRadius();
	}

	@Override
	public double getVisibilityCheckDistance() {
		return SettingAddon.getCopSpawnVisibilityCheckDistance();
	}

	@Override
	public int getSpawnPhase1Attempts() {
		return SettingAddon.getCopSpawnPhase1Attempts();
	}

	@Override
	public int getSpawnPhase2Attempts() {
		return SettingAddon.getCopSpawnPhase2Attempts();
	}

	@Override
	public int getNavigationRecalculationTicks() {
		return SettingAddon.getCopNavRecalculationTicks();
	}

	@Override
	public int getStuckCheckIntervalTicks() {
		return SettingAddon.getCopNavStuckCheckInterval();
	}

	@Override
	public int getMaxStuckChecks() {
		return SettingAddon.getCopNavMaxStuckChecks();
	}

	@Override
	public int getMaxHopelessStuckChecks() {
		return SettingAddon.getCopNavMaxHopelessStuckChecks();
	}

	@Override
	public double getHopelessCloseThreshold() {
		return SettingAddon.getCopNavHopelessCloseThreshold();
	}

	@Override
	public double getMinProgressDistance() {
		return SettingAddon.getCopNavMinProgressDistance();
	}

	@Override
	public double getRangedMinDistance() {
		return SettingAddon.getCopNavRangedMinDistance();
	}

	@Override
	public double getRangedMaxDistance() {
		return SettingAddon.getCopNavRangedMaxDistance();
	}

	@Override
	public int getMaxReturnTicks() {
		return SettingAddon.getCopReturnMaxTicks();
	}

	@Override
	public double getStationArrivalDistance() {
		return SettingAddon.getCopReturnStationArrivalDistance();
	}

	@Override
	public int getStartingAmmoMagazines() {
		return SettingAddon.getCopStartingAmmoMagazines();
	}
}
