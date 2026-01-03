package me.luckyraven.loot.events.lootchest;

import lombok.Getter;
import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.loot.data.LootChestSession;
import me.luckyraven.loot.events.LootChestEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

public class LootChestCloseEvent extends LootChestEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final LootChestSession lootChestSession;

	private boolean cancelled;

	public LootChestCloseEvent(LootChestData lootChestData, LootChestSession lootChestSession) {
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
	public @NonNull HandlerList getHandlers() {
		return handler;
	}

}