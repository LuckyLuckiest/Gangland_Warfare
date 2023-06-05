package me.luckyraven.wanted;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.user.User;
import org.jetbrains.annotations.NotNull;

public class Wanted {

	private @Getter
	final           User<?> user;
	private @Getter int     level;
	private @Setter int     increments;
	private @Getter
	@Setter         int     maxLevel;
	private @Getter
	@Setter         boolean wanted;

	public Wanted(@NotNull User<?> user) {
		this.user = user;
		this.level = 0;
		this.increments = 1;
		this.maxLevel = 5;
		this.wanted = false;
	}

	public Wanted(@NotNull User<?> user, int level) {
		this(user);
		setLevel(level);
	}

	public void setLevel(int level) {
		this.level = Math.max(0, Math.min(level, maxLevel));
		this.wanted = this.level > 0;
	}

	public void incrementLevel() {
		setLevel(increments);
	}

	public String getLevelStr() {
		StringBuilder builder = new StringBuilder(maxLevel);
		builder.append("★".repeat(level));
		builder.append("☆".repeat(Math.max(0, maxLevel - builder.length())));
		return builder.toString();
	}

}
