package org.luckyraven.gangland.item.event;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by {@code gangland-impl} after a player's user data has finished loading and the player is ready to receive
 * item-related side-effects (e.g. join-given unique items).
 *
 * <p>This event lives in {@code gangland-item} so listeners in this module never need to import the
 * impl-side {@code UserDataInitEvent}. The bridge from {@code UserDataInitEvent} → {@code PlayerItemInitEvent} is
 * registered in {@code gangland-impl}.
 */
@Getter
public class PlayerItemInitEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;

	public PlayerItemInitEvent(@NotNull Player player, boolean async) {
		super(async);
		this.player = player;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

}
