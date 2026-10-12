package org.luckyraven.gangland.events.gangs;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A gang was created. Carries ids and names only, never the module's Gang type.
 *
 * @since gangland-api 2.6
 */
public class GangCreatedEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final int gangId;
	private final String name;
	private final UUID ownerUuid;

	public GangCreatedEvent(int gangId, String name, UUID ownerUuid) {
		this.gangId = gangId;
		this.name = name;
		this.ownerUuid = ownerUuid;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public int gangId() {
		return gangId;
	}

	public String name() {
		return name;
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

}
