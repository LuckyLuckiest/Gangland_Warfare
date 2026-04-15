package me.luckyraven.copsncrooks.events.trader;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class TraderTipEvent extends TraderEvent implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final double amount;

	@Setter
	private boolean cancelled;

	public TraderTipEvent(Player player, TraderNpc trader, double amount) {
		super(player, trader);
		this.amount = amount;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
