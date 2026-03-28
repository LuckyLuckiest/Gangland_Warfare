package me.luckyraven.gadget.repair.events;

import me.luckyraven.gadget.repair.material.RepairMaterial;
import me.luckyraven.item.repair.Repairable;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RepairStartEvent extends RepairEvent implements Cancellable {

	private static final HandlerList HANDLER_LIST = new HandlerList();

	private boolean cancelled;

	public RepairStartEvent(@NotNull Player player, @NotNull Repairable repairable,
	                        @NotNull RepairMaterial repairMaterial) {
		super(player, repairable, repairMaterial);
	}

	public static HandlerList getHandlerList() {
		return HANDLER_LIST;
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
		return HANDLER_LIST;
	}
}
