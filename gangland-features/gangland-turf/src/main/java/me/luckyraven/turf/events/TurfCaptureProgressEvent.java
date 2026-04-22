package me.luckyraven.turf.events;

import lombok.Getter;
import me.luckyraven.turf.data.Turf;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired only when capture progress <b>crosses upward</b> through a configured milestone (default 25 / 50 / 75).
 * Down-crossings are not emitted, so a see-saw fight does not flood listeners.
 */
@Getter
public final class TurfCaptureProgressEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final Turf   turf;
	private final double progress;

	public TurfCaptureProgressEvent(Turf turf, double progress) {
		this.turf     = turf;
		this.progress = progress;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
