package me.luckyraven.loot.events.lootchest;

import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.loot.events.LootChestEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

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
	public @NonNull HandlerList getHandlers() {
		return handler;
	}

}
