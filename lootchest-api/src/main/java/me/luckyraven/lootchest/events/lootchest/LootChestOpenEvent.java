package me.luckyraven.lootchest.events.lootchest;

import lombok.Getter;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.lootchest.data.LootChestSession;
import me.luckyraven.lootchest.events.LootChestEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

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
