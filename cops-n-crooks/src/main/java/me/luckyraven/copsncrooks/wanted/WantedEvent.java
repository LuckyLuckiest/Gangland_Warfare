package me.luckyraven.copsncrooks.wanted;

import lombok.Getter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WantedEvent extends Event implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final Wanted wanted;

	private boolean cancelled;

	public WantedEvent(boolean async, Wanted wanted) {
		super(async);

		this.wanted    = wanted;
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
