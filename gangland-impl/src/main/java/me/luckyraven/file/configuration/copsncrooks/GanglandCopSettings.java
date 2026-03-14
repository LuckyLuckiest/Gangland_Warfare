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
					double result = new ExpressionBuilder(formula)
							.variables("level", "base", "perLevel", "max")
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
}
