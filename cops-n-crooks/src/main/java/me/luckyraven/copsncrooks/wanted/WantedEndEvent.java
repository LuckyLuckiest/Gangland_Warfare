package me.luckyraven.copsncrooks.wanted;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class WantedEndEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final Player player;
	private final Wanted wanted;

	public WantedEndEvent(Player player, Wanted wanted) {
		super(false);

		this.player = player;
		this.wanted = wanted;
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
