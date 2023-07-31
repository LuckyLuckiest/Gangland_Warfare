package me.luckyraven.bounty;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class BountyEvent extends Event implements Cancellable {

	private static final HandlerList  handler = new HandlerList();
	private final        User<Player> user;
	private final        Bounty       bounty;

	@Setter
	private double  amountApplied;
	private boolean cancelled;

	public BountyEvent(User<Player> user) {
		this.user = user;
		this.bounty = user.getBounty();
		this.cancelled = false;
		this.amountApplied = bounty.getAmount();
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}

}
