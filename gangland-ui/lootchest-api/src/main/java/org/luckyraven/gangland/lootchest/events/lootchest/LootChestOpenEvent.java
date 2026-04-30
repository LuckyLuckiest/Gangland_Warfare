package org.luckyraven.gangland.lootchest.events.lootchest;

import lombok.Getter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootChestSession;
import org.luckyraven.gangland.lootchest.events.LootChestEvent;

public class LootChestOpenEvent extends LootChestEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final LootChestSession lootChestSession;

	private boolean cancelled;

	public LootChestOpenEvent(LootChestData lootChestData, LootChestSession lootChestSession) {
		super(lootChestData);

		this.lootChestSession = lootChestSession;
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
