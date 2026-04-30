package org.luckyraven.gangland.gang.events.bounty;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.luckyraven.gangland.economy.Currency;
import org.luckyraven.gangland.gang.bounty.Bounty;

import java.math.BigDecimal;

@Getter
@Setter
public abstract class BountyEvent extends Event implements Cancellable {

	private final Bounty bounty;

	private BigDecimal amountApplied;
	private boolean    cancelled;

	public BountyEvent(boolean async, Bounty bounty, BigDecimal amountApplied) {
		super(async);

		this.bounty        = bounty;
		this.amountApplied = Currency.of(amountApplied);
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
