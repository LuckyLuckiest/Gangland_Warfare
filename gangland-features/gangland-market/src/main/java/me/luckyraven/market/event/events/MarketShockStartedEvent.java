package me.luckyraven.market.event.events;

import lombok.Getter;
import me.luckyraven.market.event.MarketShock;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public final class MarketShockStartedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final MarketShock shock;

	public MarketShockStartedEvent(MarketShock shock) {
		this.shock = shock;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
