package me.luckyraven.core.downed;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player enters the GTA-style downed state instead of actually dying. Other systems (e.g. cop AI) should
 * treat this the same as a player death.
 */
@Getter
public class PlayerDownedEvent extends PlayerEvent {

	private static final HandlerList handler = new HandlerList();

	public PlayerDownedEvent(Player player) {
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
