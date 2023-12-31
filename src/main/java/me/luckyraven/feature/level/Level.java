package me.luckyraven.feature.level;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.datastructure.ScientificCalculator;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class Level {

	private final int    maxLevel;
	private final double baseAmount;

	private double experience;
	private int    levelValue;

	private String formula;

	public Level() {
		this(SettingAddon.getUserMaxLevel(), SettingAddon.getUserLevelBaseAmount());
	}

	public Level(int maxLevel, double baseAmount) {
		this.experience = 0D;
		this.levelValue = 0;
		this.maxLevel = maxLevel;
		this.baseAmount = baseAmount;
	}

	public void addExperience(double experience, boolean levelUp, @Nullable LevelUpEvent event) {
		this.experience += experience;
		if (levelUp) handleLevelProgression(event);
	}

	public void addExperience(double experience, @Nullable LevelUpEvent event) {
		addExperience(experience, true, event);
	}

	public void removeExperience(double experience) {
		this.experience = Math.max(this.experience - experience, 0);
	}

	public int nextLevel() {
		return Math.min(levelValue + 1, maxLevel);
	}

	public int previousLevel() {
		return Math.max(levelValue - 1, 0);
	}

	public double getPercentage() {
		return experience / experienceCalculation(nextLevel()) * 100;
	}

	public int addLevels(int levels) {
		int counter = 0;

		while (levels > 0) {
			double requiredExp = experienceCalculation(nextLevel());
			if (experience >= requiredExp) experience -= requiredExp;

			if (counter >= maxLevel) break;

			LevelUpEvent event = new LevelUpEvent(this);
			Bukkit.getPluginManager().callEvent(event);

			if (!event.isCancelled()) ++levelValue;

			--levels;
			++counter;
		}

		return counter;
	}

	public int removeLevels(int levels) {
		int counter = 0;

		while (levels > 0 && levelValue > 0) {
			--levelValue;
			--levels;
			++counter;
		}

		return counter;
	}

	private void handleLevelProgression(LevelUpEvent event) {
		double nextLevelAmount = experienceCalculation(nextLevel());

		while (levelValue < maxLevel && experience >= nextLevelAmount) {
			experience -= nextLevelAmount;

			if (event != null) {
				Bukkit.getPluginManager().callEvent(event);

				if (!event.isCancelled()) ++levelValue;
			} else ++levelValue;

			nextLevelAmount = experienceCalculation(nextLevel());
		}
	}

	public double experienceCalculation(int level) {
		Map<String, Double> variables = new HashMap<>();

		variables.put("base", baseAmount);
		variables.put("max", (double) maxLevel);
		variables.put("level", (double) level);
		variables.put("experience", experience);

		String formula =
				this.formula == null || this.formula.isEmpty() ? SettingAddon.getUserLevelFormula() : this.formula;

		ScientificCalculator calculator = new ScientificCalculator(formula, variables);

		return calculator.evaluate();
	}

	@Override
	public String toString() {
		return String.format("Level{level=%d,experience=%.2f,max_level=%d}", levelValue, experience, maxLevel);
	}

}
