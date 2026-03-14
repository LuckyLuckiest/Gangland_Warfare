package me.luckyraven.copsncrooks.events.bounty;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.bounty.Bounty;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

@Getter
@Setter
public abstract class BountyEvent extends Event implements Cancellable {

	private final Bounty bounty;

	private double  amountApplied;
	private boolean cancelled;

	public BountyEvent(boolean async, Bounty bounty, double amountApplied) {
		super(async);

		this.bounty        = bounty;
		this.amountApplied = amountApplied;
		this.cancelled     = false;
	}

	public BountyEvent(boolean async, Bounty bounty) {
		this(async, bounty, bounty.getAmount());
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}
}
