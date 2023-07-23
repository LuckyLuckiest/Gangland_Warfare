package me.luckyraven.wanted;

import lombok.Getter;
import me.luckyraven.data.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class WantedEvent extends Event implements Cancellable {

	private static final HandlerList  handler = new HandlerList();
	@Getter
	private final        User<Player> user;
	@Getter
	private final        Wanted       wanted;
	private              boolean      cancelled;

	public WantedEvent(User<Player> user) {
		this.user = user;
		this.wanted = user.getWanted();
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
