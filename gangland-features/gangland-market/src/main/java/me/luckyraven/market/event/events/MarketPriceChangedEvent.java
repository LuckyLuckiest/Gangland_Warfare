package me.luckyraven.market.event.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public final class MarketPriceChangedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final String itemId;
	private final double previousPrice;
	private final double newPrice;

	public MarketPriceChangedEvent(String itemId, double previousPrice, double newPrice) {
		this.itemId        = itemId;
		this.previousPrice = previousPrice;
		this.newPrice      = newPrice;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	public double delta() {
		return newPrice - previousPrice;
	}

	public double percentageChange() {
		if (previousPrice == 0D) {
			return 0D;
		}
		return (newPrice - previousPrice) / previousPrice;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
