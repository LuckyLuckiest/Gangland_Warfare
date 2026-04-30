package org.luckyraven.gangland.item.fuel;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Narrow contract used by gangland-item fuel listeners to look up registered {@link Fuel} definitions and clear
 * per-player caches without importing the concrete {@code org.luckyraven.gangland.gadget.fuel.FuelService} class (which
 * lives in gangland-gadget and carries inventory-state APIs the listeners do not need).
 *
 * <p>The concrete service in gangland-gadget {@code implements} this interface so a single instance satisfies both
 * the in-module callers and the listener side.
 */
public interface FuelContract {

	/**
	 * Returns the fuel definition for the given key, or {@code null} if not registered.
	 */
	@Nullable
	Fuel getFuel(String fuelKey);

	/**
	 * Clears any cached per-player slot lookups. Called on disconnect by the hold-display listener.
	 */
	void clearCache(UUID playerId);

}
