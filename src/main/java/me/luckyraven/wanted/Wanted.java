package me.luckyraven.wanted;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.user.User;
import org.jetbrains.annotations.NotNull;

public class Wanted {

	private @Getter
	final           User<?> user;
	private @Getter int     level;

	private @Getter
	@Setter boolean wanted;

	public Wanted(@NotNull User<?> user) {
		this.user = user;
		this.level = 0;
		this.wanted = false;
	}

	public Wanted(@NotNull User<?> user, int level) {
		this(user);
		setLevel(level);
	}

	public void setLevel(int level) {
		if (level < 0) level = 0;
		else if (level > 5) level = 5;
		this.level = level;
		this.wanted = this.level > 0;
	}

}
