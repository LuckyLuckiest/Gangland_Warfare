package org.luckyraven.gangland.copsncrooks.scanner;

import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.UUID;
import java.util.function.Function;

/**
 * Per-world {@link MapView} creation and renderer attachment. The map id lives on the item, so a map given before a
 * restart keeps working: holding it re-attaches the renderer (P-MAP-2).
 *
 * @since 0.20.0
 */
public class ScannerMapView {

	public ScannerMapView(ScannerMapRenderer renderer, Function<World, MapView> mapFactory) {
		// TDD skeleton: no state yet
	}

	/** Our renderer is the only one on {@code view}; tracking is off. Idempotent. */
	public void attach(MapView view) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The view this class created for {@code world}, created lazily. */
	public MapView viewFor(World world) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * Makes {@code item} a working Scanner Map: binds a view when it has none (or its map is gone) and attaches the
	 * renderer. Returns {@code false} when the item is not a map.
	 */
	public boolean ensure(ItemStack item, World world) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Drops per-player renderer state (quit). */
	public void forget(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
