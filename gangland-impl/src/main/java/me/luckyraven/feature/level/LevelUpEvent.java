package me.luckyraven.feature.level;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.user.User;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class LevelUpEvent extends Event implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final Level level;

	@Nullable
	@Setter
	private User<? extends OfflinePlayer> user;
	@Nullable
	@Setter
	private Gang                          gang;

	private boolean cancelled;

	public LevelUpEvent(Level level) {
		this.level     = level;
		this.cancelled = false;
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
