package me.luckyraven.events.user;

import lombok.Getter;
import me.luckyraven.copsncrooks.events.bounty.BountyEvent;
import me.luckyraven.data.account.user.User;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

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
