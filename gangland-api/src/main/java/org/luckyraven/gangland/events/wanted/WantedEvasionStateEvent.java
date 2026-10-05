package org.luckyraven.gangland.events.wanted;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The evasion clock changed state, or (while SEARCHING) the seconds left changed. {@code secondsLeft} is 0 unless
 * SEARCHING; {@code zoneCentre} is non-null in SEARCHING and EVADED; {@code level} is the stars after the change.
 */
@Getter
public class WantedEvasionStateEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player       player;
	private final EvasionState state;
	private final int          level;
	private final int          secondsLeft;
	private final @Nullable Location zoneCentre;
	private final double       zoneRadius;

	public WantedEvasionStateEvent(Player player, EvasionState state, int level, int secondsLeft,
	                               @Nullable Location zoneCentre, double zoneRadius) {
		super(false);
		this.player      = player;
		this.state       = state;
		this.level       = level;
		this.secondsLeft = secondsLeft;
		this.zoneCentre  = zoneCentre;
		this.zoneRadius  = zoneRadius;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
