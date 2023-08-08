package me.luckyraven.level;

import lombok.Getter;
import lombok.Setter;

public class Level {

	private @Getter
	@Setter double amount;
	private int    maxLevel;
	private double baseAmount;

	public Level() {
		this(100, 0D);
	}

	public Level(int maxLevel, double baseAmount) {
		this.amount = 0D;
		this.maxLevel = maxLevel;
		this.baseAmount = baseAmount;
	}

	public void addExperience(double experience) {
		if (amount + experience < 0) return;

		amount += experience;
	}

	@Override
	public String toString() {
		return String.format("{amount=%.2f}", amount);
	}

}
