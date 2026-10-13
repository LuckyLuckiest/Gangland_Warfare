package org.luckyraven.gangland.events.gangs;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.data.gang.GangChatChannel;

/**
 * A gang or ally chat message is about to be delivered. Fired on the main thread; cancelling stops delivery.
 *
 * @since gangland-api 2.6
 */
public class GangChatEvent extends Event implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	private final Player sender;
	private final int gangId;
	private final GangChatChannel channel;
	private final String message;

	private boolean cancelled;

	public GangChatEvent(Player sender, int gangId, GangChatChannel channel, String message) {
		this.sender = sender;
		this.gangId = gangId;
		this.channel = channel;
		this.message = message;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public Player sender() {
		return sender;
	}

	public int gangId() {
		return gangId;
	}

	public GangChatChannel channel() {
		return channel;
	}

	public String message() {
		return message;
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
		return handlers;
	}

}
