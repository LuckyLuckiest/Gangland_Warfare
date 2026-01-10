package me.luckyraven.copsncrooks.wanted;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WantedStartEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final Player player;
	private final Wanted wanted;
	private final int    wantedLevel;

	public WantedStartEvent(Player player, Wanted wanted, int wantedLevel) {
		super(false);

		this.player      = player;
		this.wanted      = wanted;
		this.wantedLevel = wantedLevel;
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
