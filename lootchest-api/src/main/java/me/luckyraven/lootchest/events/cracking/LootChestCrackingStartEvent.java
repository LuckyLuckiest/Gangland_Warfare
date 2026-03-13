package me.luckyraven.lootchest.events.cracking;

import lombok.Getter;
import me.luckyraven.lootchest.data.CrackingSession;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.lootchest.events.LootChestEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class LootChestCrackingStartEvent extends LootChestEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final CrackingSession crackingSession;

	private boolean cancelled;

	public LootChestCrackingStartEvent(LootChestData lootChestData, CrackingSession crackingSession) {
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
