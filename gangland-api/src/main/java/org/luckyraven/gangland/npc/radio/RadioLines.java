package org.luckyraven.gangland.npc.radio;

import java.util.List;

/**
 * The lines available for one radio/shout key, e.g. {@code "Contact"} or {@code "Ack"}. An empty list means that
 * key is silent — nothing is ever spoken for it. Beyond the per-signal keys, {@link SquadRadio} looks up a handful
 * of special keys through the same interface: {@code "Format"} and {@code "Dispatch_Format"} (each a single-element
 * list wrapping the line-wrapping template), {@code "Compass"} (8 words, starting at north and going clockwise) and
 * {@code "Sides"} (front, left, right, behind).
 *
 * @since 1.13.0
 */
@FunctionalInterface
public interface RadioLines {

	/** The candidate lines for {@code key}; empty means silent. */
	List<String> lines(String key);
}
