package me.luckyraven.wanted;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Wanted {

	@Getter
	private final User<Player> user;
	@Getter
	private       int     level;
	@Setter
	private       int     increments;
	@Getter
	@Setter
	private       int     maxLevel;
	@Getter
	@Setter
	private       boolean wanted;

	public Wanted(@NotNull User<Player> user, int level) {
		this(user);
		setLevel(level);
	}

	public Wanted(@NotNull User<Player> user) {
		this.user = user;
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
		setLevel(increments);
	}

	public String getLevelStr() {
		StringBuilder builder = new StringBuilder(maxLevel);
		builder.append("★".repeat(level)).append("☆".repeat(Math.max(0, maxLevel - builder.length())));
		return builder.toString();
	}

}
