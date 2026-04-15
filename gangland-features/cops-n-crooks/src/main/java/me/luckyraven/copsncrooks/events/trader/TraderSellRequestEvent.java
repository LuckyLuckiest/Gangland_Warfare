package me.luckyraven.copsncrooks.events.trader;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class TraderSellRequestEvent extends TraderEvent implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final ShopItemEntry entry;
	private final double        payout;

	@Setter
	private boolean cancelled;
	@Setter
	private String  reason;

	public TraderSellRequestEvent(Player player, TraderNpc trader, ShopItemEntry entry, double payout) {
		super(player, trader);
		this.entry  = entry;
		this.payout = payout;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
