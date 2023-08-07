package me.luckyraven.level;

import lombok.Getter;
import lombok.Setter;

public class Level {

	private @Getter
	@Setter double amount;

	public Level() {
		this.amount = 0D;
	}

	// TODO

	@Override
	public String toString() {
		return String.format("{amount=%.2f}", amount);
	}

}
