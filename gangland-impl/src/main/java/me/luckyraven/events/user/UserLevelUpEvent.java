package me.luckyraven.events.user;

import lombok.Getter;
import me.luckyraven.data.account.user.User;
import me.luckyraven.events.level.LevelUpEvent;
import me.luckyraven.features.level.Level;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class UserLevelUpEvent extends LevelUpEvent {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final User<?> user;

	public UserLevelUpEvent(boolean async, User<?> user, Level level) {
		super(async, level);

		this.user = user;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}

}
