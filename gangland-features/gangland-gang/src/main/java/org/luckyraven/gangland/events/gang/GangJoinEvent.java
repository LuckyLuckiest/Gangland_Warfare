package org.luckyraven.gangland.events.gang;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.gang.Gang;

/**
 * Fired on the main thread after a player joined an open gang with {@code /glw gang join}, so modules the gang module
 * cannot depend on (mail) can drop what the player still has pending. Invite accept does its own sibling cancel and
 * does not fire this event.
 */
public class GangJoinEvent extends Event {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final Gang gang;

	@Getter
	private final Player player;

	public GangJoinEvent(Gang gang, Player player) {
		this.gang   = gang;
		this.player = player;
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
