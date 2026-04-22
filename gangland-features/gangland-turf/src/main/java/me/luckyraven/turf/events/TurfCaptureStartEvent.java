package me.luckyraven.turf.events;

import lombok.Getter;
import me.luckyraven.gang.Gang;
import me.luckyraven.turf.data.Turf;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired the instant a challenger gang begins contesting a turf — the tracker has just moved the turf's state from IDLE
 * to CONTESTING.
 */
@Getter
public final class TurfCaptureStartEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Turf turf;
	private final Gang challengerGang;

	public TurfCaptureStartEvent(Turf turf, Gang challengerGang) {
		this.turf           = turf;
		this.challengerGang = challengerGang;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
