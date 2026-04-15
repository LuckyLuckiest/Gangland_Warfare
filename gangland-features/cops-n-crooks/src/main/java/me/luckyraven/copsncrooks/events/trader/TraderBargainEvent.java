package me.luckyraven.copsncrooks.events.trader;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.mood.BargainResult;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class TraderBargainEvent extends TraderEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	private final ShopItemEntry entry;
	private final double        offerRatio;
	private final int           roundsUsed;

	@Setter
	private BargainResult result;

	public TraderBargainEvent(Player player, TraderNpc trader, ShopItemEntry entry,
	                          double offerRatio, int roundsUsed) {
		super(player, trader);
		this.entry      = entry;
		this.offerRatio = offerRatio;
		this.roundsUsed = roundsUsed;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
