package org.luckyraven.gangland.turf.events;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Fired by the turf location tracker when a player's 1-Hz detection tick places them outside a turf they were inside on
 * the previous tick.
 */
@Getter
public final class TurfExitEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final Turf   turf;

	public TurfExitEvent(Player player, Turf turf) {
		this.player = player;
		this.turf   = turf;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
