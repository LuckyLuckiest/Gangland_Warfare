package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/**
 * Repeating main-thread task that refreshes {@code Bag: $X} on the action bar every {@code Hud_Interval_Ticks}.
 *
 * @since 0.20.0
 */
public final class BagHud {

	public BagHud(JavaPlugin plugin, BagService service, Supplier<BagSettings> settings) {
	}

	/** Starts (or restarts) the repeating task at the configured interval. */
	public void start() {
		throw new UnsupportedOperationException("TDD stub");
	}

	public void stop() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
