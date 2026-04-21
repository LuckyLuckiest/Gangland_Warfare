package me.luckyraven.copsncrooks.events.trader;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Getter
public class TraderSellRequestEvent extends TraderEvent implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final List<ItemStack> offeredItems;
	private final BigDecimal      finalOffer;

	@Setter
	private boolean cancelled;
	@Setter
	private String  reason;

	public TraderSellRequestEvent(Player player, TraderNpc trader, List<ItemStack> offeredItems,
	                              BigDecimal finalOffer) {
		super(player, trader);
		this.offeredItems = offeredItems == null ? Collections.emptyList() : List.copyOf(offeredItems);
		this.finalOffer   = finalOffer;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
