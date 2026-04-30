package org.luckyraven.gangland.copsncrooks.events.trader;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderNpc;
import org.luckyraven.gangland.shop.ShopItemEntry;

import java.math.BigDecimal;

@Getter
public class TraderBuyRequestEvent extends TraderEvent implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final ShopItemEntry entry;
	private final BigDecimal    finalPrice;
	private final int           quantity;

	@Setter
	private boolean cancelled;
	@Setter
	private String  reason;

	public TraderBuyRequestEvent(Player player, TraderNpc trader, ShopItemEntry entry, BigDecimal finalPrice) {
		this(player, trader, entry, finalPrice, 1);
	}

	public TraderBuyRequestEvent(Player player, TraderNpc trader, ShopItemEntry entry, BigDecimal finalPrice,
	                             int quantity) {
		super(player, trader);
		this.entry      = entry;
		this.finalPrice = finalPrice;
		this.quantity   = Math.max(1, quantity);
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
