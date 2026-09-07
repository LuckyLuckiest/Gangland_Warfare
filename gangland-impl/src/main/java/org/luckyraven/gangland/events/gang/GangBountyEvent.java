package org.luckyraven.gangland.events.gang;

import lombok.Getter;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.events.bounty.BountyEvent;

import java.math.BigDecimal;

@Getter
public class GangBountyEvent extends BountyEvent {

	private static final HandlerList handler = new HandlerList();

	private final Gang gang;

	public GangBountyEvent(boolean async, Gang gang, BigDecimal amountApplied) {
		super(async, gang.getBounty(), amountApplied);

		this.gang = gang;
	}

	public GangBountyEvent(boolean async, Gang gang) {
		this(async, gang, gang.getBounty().getAmount());
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}
}

