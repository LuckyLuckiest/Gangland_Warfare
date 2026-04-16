package me.luckyraven.market.event.events;

import lombok.Getter;
import me.luckyraven.market.ledger.TransactionRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public final class MarketTransactionEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final TransactionRecord record;

	public MarketTransactionEvent(TransactionRecord record) {
		this.record = record;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
