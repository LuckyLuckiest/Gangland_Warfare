package org.luckyraven.gangland.events.crime;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A player committed a crime. Publishers fire it through {@code CrimeService}; the heat ledger turns it into stars.
 * Cancelling it makes the crime count for nothing. Crime ids are plain strings (see {@code Crimes}).
 */
@Getter
public class CrimeCommittedEvent extends Event implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player   player;
	private final String   crimeId;
	private final Location location;
	/**
	 * What the publisher knows; the heat ledger additionally applies its own squad check.
	 */
	private final boolean  seenByCop;
	private final int      witnesses;

	private boolean cancelled;

	public CrimeCommittedEvent(Player player, String crimeId, Location location, boolean seenByCop, int witnesses) {
		super(false);
		this.player    = player;
		this.crimeId   = crimeId;
		this.location  = location.clone();
		this.seenByCop = seenByCop;
		this.witnesses = witnesses;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

}
