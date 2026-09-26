package org.luckyraven.gangland.events.gang;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.gang.Gang;

/**
 * Fired on the main thread after a gang is disbanded and removed from the repository and {@code GangManager}, so
 * modules the gang module cannot depend on (mail) can drop what still points at it.
 */
public class GangDeleteEvent extends Event {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final Gang gang;

	public GangDeleteEvent(Gang gang) {
		this.gang = gang;
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
