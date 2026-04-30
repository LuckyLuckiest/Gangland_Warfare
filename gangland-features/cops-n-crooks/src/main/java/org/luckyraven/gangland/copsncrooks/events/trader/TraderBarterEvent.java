package org.luckyraven.gangland.copsncrooks.events.trader;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderNpc;
import org.luckyraven.gangland.shop.ShopItemEntry;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Fired when the player confirms a trader barter. Barter is a pure item-for-item swap — no economy money changes hands.
 * {@link #askingValue()} is the trader's asking threshold; {@link #offeredValue()} is the computed value of the items
 * in {@link #offered()}. A listener cancels the event if the swap cannot be completed.
 */
@Getter
public class TraderBarterEvent extends TraderEvent implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final ShopItemEntry   entry;
	private final BigDecimal      askingValue;
	private final BigDecimal      offeredValue;
	private final List<ItemStack> offered;

	@Setter
	private boolean cancelled;
	@Setter
	private String  reason;

	public TraderBarterEvent(Player player, TraderNpc trader, ShopItemEntry entry, BigDecimal askingValue,
	                         BigDecimal offeredValue, List<ItemStack> offered) {
		super(player, trader);
		this.entry        = entry;
		this.askingValue  = askingValue;
		this.offeredValue = offeredValue;
		this.offered      = offered == null ? Collections.emptyList() : List.copyOf(offered);
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
