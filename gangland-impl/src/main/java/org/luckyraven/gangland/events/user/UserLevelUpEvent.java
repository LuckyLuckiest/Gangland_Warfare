package org.luckyraven.gangland.events.user;

import lombok.Getter;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.gang.events.level.LevelUpEvent;
import org.luckyraven.gangland.gang.user.Level;
import org.luckyraven.gangland.gang.user.User;

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
