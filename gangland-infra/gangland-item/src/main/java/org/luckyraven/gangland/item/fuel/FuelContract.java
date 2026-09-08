package org.luckyraven.gangland.item.fuel;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Narrow contract used by gangland-item fuel listeners to look up registered {@link Fuel} definitions and clear
 * per-player caches without importing the concrete {@code org.luckyraven.gangland.item.fuel.FuelService} class (which
 * carries inventory-state APIs the listeners do not need).
 *
 * <p>The concrete {@code FuelService} {@code implements} this interface so a single instance satisfies both
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

	/**
	 * {@code true} for an item that <em>stores</em> fuel and can be refuelled from a fuel container — a jetpack, or
	 * any other wearable a fuel-owning module registers. {@code false} (the default) for a plain fuel container
	 * (e.g. a gasoline can), so a container never drains into another container and a sink never drains into a
	 * container. gangland-item has no wearable catalog of its own; a fuel-owning module (gadget/Bartizan) overrides
	 * this against its own catalog.
	 */
	default boolean isFuelSink(ItemStack stack) {
		return false;
	}

}
