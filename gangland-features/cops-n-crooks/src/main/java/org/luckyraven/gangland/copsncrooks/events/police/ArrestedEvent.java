package org.luckyraven.gangland.copsncrooks.events.police;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A player was admitted to jail (0.16.1 T-187). The arrest of a player at zero stars, a searched one, fires no wanted end,
 * so the chase is ended through this event instead.
 */
@Getter
public class ArrestedEvent extends Event {

	private static final HandlerList handler = new HandlerList();

	private final Player player;

	public ArrestedEvent(Player player) {
		this.player = player;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}
}
