package org.luckyraven.gangland.civilians.events;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A civilian's 911 call landed: {@code criminal} did {@code crimeId} at {@code at} and {@code caller} reported it.
 * {@code viewers} (0.21.2) is the caller plus every civilian who also saw it and is still alive at landing: the head
 * count the heat ledger scores. Fired on the main thread by {@code WitnessService}; cops-n-crooks turns it into the
 * counted crime.
 *
 * @since 0.20.0
 */
@Getter
public class CivilianCalledInEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Player       criminal;
	private final String       crimeId;
	private final Location     at;
	private final LivingEntity caller;
	private final int          viewers;

	/** A call with the caller as its only viewer. */
	public CivilianCalledInEvent(Player criminal, String crimeId, Location at, LivingEntity caller) {
		this(criminal, crimeId, at, caller, 1);
	}

	public CivilianCalledInEvent(Player criminal, String crimeId, Location at, LivingEntity caller, int viewers) {
		super(false);
		this.criminal = criminal;
		this.crimeId  = crimeId;
		this.at       = at.clone();
		this.caller   = caller;
		this.viewers  = Math.max(1, viewers);
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
