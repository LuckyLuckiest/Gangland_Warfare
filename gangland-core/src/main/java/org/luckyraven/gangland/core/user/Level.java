package org.luckyraven.gangland.core.user;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.datastructure.ScientificCalculator;
import org.luckyraven.gangland.core.events.level.LevelUpEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * An XP curve with a level counter. Level-up events fire after each level's increment, so a listener reads the level
 * just reached; cancelling one undoes that step and stops further levels (T-253).
 */
@Getter
@Setter
public class Level {

	private final int    maxLevel;
	private final double baseAmount;

	@Setter(AccessLevel.NONE)
	private double experience;
	private int    levelValue;

	private String formula;

	public Level() {
		this(IdentitySettings.getUserMaxLevel(), IdentitySettings.getUserLevelBaseAmount());
	}

	public Level(int maxLevel, double baseAmount) {
		this.experience = 0D;
		this.levelValue = 0;
		this.maxLevel   = maxLevel;
		this.baseAmount = baseAmount;
	}

	public void addExperience(double experience, boolean levelUp, @Nullable LevelUpEvent event) {
		if (!Double.isFinite(experience)) return;
		if (levelValue >= maxLevel) return;   // T-255: no XP collects at the top level
		this.experience += experience;
		if (levelUp) handleLevelProgression(event);
	}

	public void addExperience(double experience, @Nullable LevelUpEvent event) {
		addExperience(experience, true, event);
	}

	public void removeExperience(double experience) {
		if (!Double.isFinite(experience)) return;
		this.experience = Math.max(this.experience - experience, 0);
	}

	/**
	 * Sets the raw experience value, mapping a non-finite input (NaN/Infinity - e.g. a row poisoned by a past
	 * {@code addExperience}/{@code removeExperience} call before those guards existed) to {@code 0} instead of
	 * storing it back.
	 */
	public void setExperience(double experience) {
		this.experience = Double.isFinite(experience) ? experience : 0D;
	}

	public int nextLevel() {
		return Math.min(levelValue + 1, maxLevel);
	}

	public int previousLevel() {
		return Math.max(levelValue - 1, 0);
	}

	public double getPercentage() {
		if (levelValue >= maxLevel) return 100D;   // T-255: a maxed bar never reads above 100 %
		return experience / experienceCalculation(nextLevel()) * 100;
	}

	public int addLevels(int levels, @Nullable LevelUpEvent event) {
		int counter = 0;

		while (levels > 0 && levelValue < maxLevel) {
			double required = experienceCalculation(nextLevel());
			double taken    = experience >= required ? required : 0D;
			experience -= taken;
			++levelValue;

			// T-253/US-43: the event fires after the increment; a cancel undoes this step and stops. T-277: null is allowed
			if (event != null) {
				Bukkit.getPluginManager().callEvent(event);

				if (event.isCancelled()) {
					--levelValue;
					experience += taken;
					break;
				}
			}

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

	public double experienceCalculation(int level) {
		Map<String, Double> variables = new HashMap<>();

		variables.put("base", baseAmount);
		variables.put("max", (double) maxLevel);
		variables.put("level", (double) level);
		variables.put("experience", experience);

		String formula = this.formula == null || this.formula.isEmpty() ?
		                 IdentitySettings.getUserLevelFormula() :
		                 this.formula;

		ScientificCalculator calculator = new ScientificCalculator(formula, variables);

		return calculator.evaluate();
	}

	@Override
	public String toString() {
		return String.format("Level{level=%d,experience=%.2f,max_level=%d}", levelValue, experience, maxLevel);
	}

	private void handleLevelProgression(@Nullable LevelUpEvent event) {
		double nextLevelAmount = experienceCalculation(nextLevel());

		while (levelValue < maxLevel && experience >= nextLevelAmount) {
			experience -= nextLevelAmount;
			++levelValue;

			// T-253: the event fires after the increment, so a listener reads the level just reached; a cancel undoes this step
			if (event != null) {
				Bukkit.getPluginManager().callEvent(event);

				if (event.isCancelled()) {
					--levelValue;
					experience += nextLevelAmount;
					return;
				}
			}

			nextLevelAmount = experienceCalculation(nextLevel());
		}
	}

}
