package me.luckyraven.feature.wanted;

import lombok.Getter;
import lombok.Setter;

public class Wanted {

	private @Getter int level;
	private @Setter int increments;

	@Getter
	@Setter
	private int     maxLevel;
	@Getter
	@Setter
	private boolean wanted;

	public Wanted(int level) {
		this();
		setLevel(level);
	}

	public Wanted() {
		this.level = 0;
		this.increments = 1;
		this.maxLevel = 5;
		this.wanted = false;
	}

	public void setLevel(int level) {
		this.level = Math.max(0, Math.min(level, maxLevel));
		this.wanted = this.level > 0;
	}

	public void incrementLevel() {
		setLevel(increments + level);
	}

	public String getLevelStr() {
		StringBuilder builder   = new StringBuilder(maxLevel);
		builder.append("★".repeat(level)).append("☆".repeat(Math.max(0, maxLevel - builder.length())));
		return builder.toString();
	}

}
