package me.luckyraven.copsncrooks.wanted;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WantedLevelChangeEvent extends Event implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	private final Player player;
	private final Wanted wanted;

	private final int oldLevel;
	private final int newLevel;

	private boolean cancelled;

	public WantedLevelChangeEvent(Player player, Wanted wanted, int oldLevel, int newLevel) {
		super(false);

		this.player    = player;
		this.wanted    = wanted;
		this.oldLevel  = oldLevel;
		this.newLevel  = newLevel;
		this.cancelled = false;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

}
