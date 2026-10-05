package org.luckyraven.gangland.core.events.wanted;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;

@Getter
public class WantedStartEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final Player      player;
	private final Wanted      wanted;
	private final int         wantedLevel;
	private final WantedCause cause;

	public WantedStartEvent(Player player, Wanted wanted, int wantedLevel) {
		this(player, wanted, wantedLevel, WantedCause.UNKNOWN);
	}

	public WantedStartEvent(Player player, Wanted wanted, int wantedLevel, WantedCause cause) {
		super(false);

		this.player      = player;
		this.wanted      = wanted;
		this.wantedLevel = wantedLevel;
		this.cause       = cause;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

}
