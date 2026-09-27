package org.luckyraven.gangland.copsncrooks.events.evasion;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.copsncrooks.evasion.EvasionSnapshot;
import org.luckyraven.gangland.copsncrooks.evasion.EvasionState;

/**
 * Fired on the main thread when a wanted player's line-of-sight evasion state changes (0.12 F2), e.g. the squad lost
 * sight of the player (SEEN to SEARCHING) or the chase ended (to NONE). Informational, not cancellable.
 */
@Getter
public class EvasionStateChangeEvent extends Event {

	private static final HandlerList handler = new HandlerList();

	private final Player          player;
	private final EvasionState    oldState;
	private final EvasionState    newState;
	private final EvasionSnapshot snapshot;

	public EvasionStateChangeEvent(Player player, EvasionState oldState, EvasionState newState,
	                               EvasionSnapshot snapshot) {
		super(false);

		this.player   = player;
		this.oldState = oldState;
		this.newState = newState;
		this.snapshot = snapshot;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}
