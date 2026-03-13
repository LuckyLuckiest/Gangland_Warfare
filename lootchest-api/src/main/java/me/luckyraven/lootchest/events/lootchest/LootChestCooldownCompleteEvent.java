package me.luckyraven.lootchest.events.lootchest;

import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.lootchest.events.LootChestEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class LootChestCooldownCompleteEvent extends LootChestEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private boolean cancelled;

	public LootChestCooldownCompleteEvent(LootChestData lootChestData) {
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
