package me.luckyraven.feature.level;

import lombok.Getter;
import me.luckyraven.data.user.User;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class UserLevelUpEvent extends LevelUpEvent {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final User<?> user;

	private boolean cancelled;

	public UserLevelUpEvent(User<?> user, Level level) {
		super(level);

		this.user = user;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}

}
