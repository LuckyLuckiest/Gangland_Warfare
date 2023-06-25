package me.luckyraven.wanted;

import lombok.Getter;
import me.luckyraven.account.type.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class WantedEvent extends Event implements Cancellable {

	private static final HandlerList handler = new HandlerList();
	private              boolean     cancelled;
	@Getter
	private final        Player      player;
	@Getter
	private final        Wanted      wanted;

	public WantedEvent(Player player) {
		this.player = player;
		this.wanted = player.getValue().getWanted();
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}

}
