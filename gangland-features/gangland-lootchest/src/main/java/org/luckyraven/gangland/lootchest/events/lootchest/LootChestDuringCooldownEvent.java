package org.luckyraven.gangland.lootchest.events.lootchest;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.events.LootChestEvent;

public class LootChestDuringCooldownEvent extends LootChestEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private boolean cancelled;

	public LootChestDuringCooldownEvent(LootChestData lootChestData) {
		super(lootChestData);
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
