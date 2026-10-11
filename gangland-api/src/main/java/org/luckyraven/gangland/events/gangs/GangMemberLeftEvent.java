package org.luckyraven.gangland.events.gangs;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player left a gang.
 *
 * @since gangland-api 2.6
 */
public class GangMemberLeftEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final int gangId;
	private final UUID player;
	private final Reason reason;

	public GangMemberLeftEvent(int gangId, UUID player, Reason reason) {
		this.gangId = gangId;
		this.player = player;
		this.reason = reason;
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

	public Reason reason() {
		return reason;
	}

	/**
	 * Why the member left.
	 *
	 * @since gangland-api 2.6
	 */
	public enum Reason {

		LEFT,
		KICKED,
		DISBANDED,
		PURGED

	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

}
