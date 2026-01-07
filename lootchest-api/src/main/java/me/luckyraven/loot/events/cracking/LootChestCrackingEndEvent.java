package me.luckyraven.loot.events.cracking;

import lombok.Getter;
import me.luckyraven.loot.data.CrackingSession;
import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.loot.events.LootChestEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

public class LootChestCrackingEndEvent extends LootChestEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final CrackingSession crackingSession;

	private boolean cancelled;

	public LootChestCrackingEndEvent(LootChestData lootChestData, CrackingSession crackingSession) {
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
	public @NonNull HandlerList getHandlers() {
		return handler;
	}

}
