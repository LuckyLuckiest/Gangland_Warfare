package org.luckyraven.gangland.events.gangs;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player joined a gang.
 *
 * @since gangland-api 2.6
 */
public class GangMemberJoinedEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final int gangId;
	private final UUID player;

	public GangMemberJoinedEvent(int gangId, UUID player) {
		this.gangId = gangId;
		this.player = player;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public int gangId() {
		return gangId;
	}

	public UUID player() {
		return player;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

}
