package org.luckyraven.gangland.events.gangs;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A gang reached a new level.
 *
 * @since gangland-api 2.6
 */
public class GangLevelReachedEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final int gangId;
	private final int level;

	public GangLevelReachedEvent(int gangId, int level) {
		this.gangId = gangId;
		this.level = level;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public int gangId() {
		return gangId;
	}

	public int level() {
		return level;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

}
