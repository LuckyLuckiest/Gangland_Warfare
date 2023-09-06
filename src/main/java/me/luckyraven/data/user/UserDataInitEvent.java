package me.luckyraven.data.user;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class UserDataInitEvent extends Event implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final User<Player> user;
	private final Player       player;

	private boolean cancelled;

	public UserDataInitEvent(User<Player> user) {
		super(true);
		this.user = user;
		this.player = user.getUser();
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
