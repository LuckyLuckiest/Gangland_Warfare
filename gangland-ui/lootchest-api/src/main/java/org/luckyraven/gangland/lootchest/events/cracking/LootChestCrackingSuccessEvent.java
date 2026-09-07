package org.luckyraven.gangland.lootchest.events.cracking;

import lombok.Getter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.lootchest.data.CrackingSession;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.events.LootChestEvent;

public class LootChestCrackingSuccessEvent extends LootChestEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final CrackingSession crackingSession;

	private boolean cancelled;

	public LootChestCrackingSuccessEvent(LootChestData lootChestData, CrackingSession crackingSession) {
		super(lootChestData);

		this.crackingSession = crackingSession;
	}

	private static HandlerList getHandlerList() {
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

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}
