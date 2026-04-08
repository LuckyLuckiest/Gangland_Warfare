package me.luckyraven.util.downed;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player respawns out of the GTA-style downed state. This is the counterpart to {@link PlayerDownedEvent}
 * — vanilla {@link org.bukkit.event.player.PlayerRespawnEvent} does not fire in this flow because the player never
 * actually died, so systems that need to react to "the player is back on their feet" should listen here instead.
 */
@Getter
public class PlayerUndownedEvent extends PlayerEvent {

	private static final HandlerList handler = new HandlerList();

	public PlayerUndownedEvent(Player player) {
		super(player);
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}
}
