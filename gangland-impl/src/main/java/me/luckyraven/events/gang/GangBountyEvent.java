package me.luckyraven.events.gang;

import lombok.Getter;
import me.luckyraven.copsncrooks.events.bounty.BountyEvent;
import me.luckyraven.data.account.gang.Gang;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class GangBountyEvent extends BountyEvent {

	private static final HandlerList handler = new HandlerList();

	private final Gang gang;

	public GangBountyEvent(boolean async, Gang gang, double amountApplied) {
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

