package me.luckyraven.turf.events;

import lombok.Getter;
import me.luckyraven.turf.data.Turf;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by the turf location tracker when a player's 1-Hz detection tick places them inside a turf they were not inside
 * on the previous tick.
 */
@Getter
public final class TurfEnterEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final Turf   turf;

	public TurfEnterEvent(Player player, Turf turf) {
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
