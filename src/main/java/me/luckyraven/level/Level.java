package me.luckyraven.level;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.datastructure.ScientificCalculator;
import me.luckyraven.file.configuration.SettingAddon;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Level {

	private final int    maxLevel;
	private final double baseAmount;

	@Setter
	private double experience;
	private int    level;

	@Setter
	private String formula;

	public Level() {
		this(SettingAddon.getUserMaxLevel(), SettingAddon.getUserLevelBaseAmount());
	}

	public Level(int maxLevel, double baseAmount) {
		this.experience = 0D;
		this.level = 0;
		this.maxLevel = maxLevel;
		this.baseAmount = baseAmount;
	}

	public void addExperience(double experience, boolean levelUp) {
		this.experience += experience;
		if (levelUp) handleLevelProgression();
	}

	public void addExperience(double experience) {
		addExperience(experience, true);
	}

	public void removeExperience(double experience) {
		this.experience = Math.max(this.experience - experience, 0);
		handleLevelProgression();
	}

	public int nextLevel() {
		return Math.min(level + 1, maxLevel);
	}

	public int previousLevel() {
		return Math.max(level - 1, 0);
	}

	public int addLevels(int levels) {
		int counter = 0;
		while (levels > 0) {
			double requiredExp = experienceCalculation(nextLevel());
			if (experience >= requiredExp) experience -= requiredExp;

			if (counter >= maxLevel) break;

			++level;
			--levels;
			++counter;
		}

		return counter;
	}

	public void removeLevels(int levels) {
		while (levels > 0 && level > 0) {
			--level;
			--levels;
		}
	}

	private void handleLevelProgression() {
		double nextLevelAmount = experienceCalculation(nextLevel());

		while (level < maxLevel && experience >= nextLevelAmount) {
			experience -= nextLevelAmount;
			++level;
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
		return String.format("{level=%d,experience=%.2f,max_level=%d}", level, experience, maxLevel);
	}

}
