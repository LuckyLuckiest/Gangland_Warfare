package org.luckyraven.gangland.gang.events.user;

import lombok.Getter;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.gang.events.bounty.BountyEvent;
import org.luckyraven.gangland.gang.user.User;

import java.math.BigDecimal;

@Getter
public class UserBountyEvent extends BountyEvent {

	private static final HandlerList handler = new HandlerList();

	private final User<?> user;

	public UserBountyEvent(boolean async, User<?> user, BigDecimal amountApplied) {
		super(async, user.getBounty(), amountApplied);
		this.user = user;
	}

	public UserBountyEvent(boolean async, User<?> user) {
		this(async, user, user.getBounty().getAmount());
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
