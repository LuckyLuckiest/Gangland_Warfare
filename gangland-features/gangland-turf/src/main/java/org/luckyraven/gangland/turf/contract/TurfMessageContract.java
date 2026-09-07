package org.luckyraven.gangland.turf.contract;

import org.bukkit.command.CommandSender;

/**
 * Routes turf-facing user strings through gangland-impl's {@code Messages} enum. Keys are referenced by name; the impl
 * verifies each key exists at bean-init time.
 */
public interface TurfMessageContract {

	String format(String key, Object... replacements);

	void send(CommandSender recipient, String key, Object... replacements);

	void broadcast(String key, Object... replacements);

	/**
	 * Renders a duration into a localised multi-unit string (e.g. {@code "1h 30m"}, {@code "45s"}) using the plugin's
	 * {@code TimeUtil} / {@code TimeMessages} machinery — same units Waypoint/Bank/Gang confirm timers use. Callers
	 * pass seconds so the contract stays free of {@link java.time} / {@code TimeUnit} imports in feature-module code.
	 */
	String formatDuration(long seconds);
}
