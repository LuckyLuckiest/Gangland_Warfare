package me.luckyraven.copsncrooks.events.trader;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@Getter
public class TraderTradeInRequestEvent extends TraderEvent implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final ShopItemEntry   entry;
	private final double          buyPrice;
	private final double          tradeInCredit;
	// Amount the trader will deposit back to the player when their item offer exceeds the buy price.
	// Zero for traders whose trait doesn't refund overpay.
	private final double          refund;
	private final List<ItemStack> offeredItems;

	@Setter
	private boolean cancelled;
	@Setter
	private String  reason;

	public TraderTradeInRequestEvent(Player player, TraderNpc trader, ShopItemEntry entry,
	                                 double buyPrice, double tradeInCredit, double refund,
	                                 List<ItemStack> offeredItems) {
		super(player, trader);
		this.entry         = entry;
		this.buyPrice      = buyPrice;
		this.tradeInCredit = tradeInCredit;
		this.refund        = refund;
		this.offeredItems  = offeredItems == null ? Collections.emptyList() : List.copyOf(offeredItems);
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
