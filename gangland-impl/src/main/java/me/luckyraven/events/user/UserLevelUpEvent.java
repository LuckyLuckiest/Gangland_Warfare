package me.luckyraven.events.user;

import lombok.Getter;
import me.luckyraven.gang.events.level.LevelUpEvent;
import me.luckyraven.gang.user.Level;
import me.luckyraven.gang.user.User;
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
