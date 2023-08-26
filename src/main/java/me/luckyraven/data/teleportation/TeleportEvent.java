package me.luckyraven.data.teleportation;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class TeleportEvent extends Event implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final Waypoint     waypoint;
	private final User<Player> user;

	private boolean cancelled;


	public TeleportEvent(Waypoint waypoint, User<Player> user) {
		this.waypoint = waypoint;
		this.user = user;
		this.cancelled = false;
	}

	public static HandlerList getHandlerList() {
		return handler;
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
