package org.luckyraven.gangland.copsncrooks.scanner;

import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Per-world {@link MapView} creation and renderer attachment. The map id lives on the item, so a map given before a
 * restart keeps working: holding it re-attaches the renderer (P-MAP-2).
 *
 * @since 0.20.0
 */
public class ScannerMapView {

	private final ScannerMapRenderer          renderer;
	private final Function<World, MapView>    mapFactory;
	// ponytail: WeakHashMap lets unloaded worlds be collected. Caveat: a MapView's getWorld() references its world, so the
	// entry only clears once the view is unreachable too. Upgrade path: a Bukkit world-unload listener that drops the entry.
	private final Map<World, MapView>         views = new WeakHashMap<>();

	public ScannerMapView(ScannerMapRenderer renderer, Function<World, MapView> mapFactory) {
		this.renderer   = renderer;
		this.mapFactory = mapFactory;
	}

	/** Our renderer is the only one on {@code view}; tracking is off. Idempotent. */
	public void attach(MapView view) {
		for (MapRenderer existing : List.copyOf(view.getRenderers())) {
			if (existing != renderer) {
				view.removeRenderer(existing);
			}
		}
		if (!view.getRenderers().contains(renderer)) {
			view.addRenderer(renderer);
		}
		view.setTrackingPosition(false);
		view.setUnlimitedTracking(false);
	}

	/** The view this class created for {@code world}, created lazily. */
	public MapView viewFor(World world) {
		MapView view = views.computeIfAbsent(world, mapFactory);
		attach(view);
		return view;
	}

	/**
	 * Makes {@code item} a working Scanner Map: binds a view when it has none (or its map is gone) and attaches the
	 * renderer. Returns {@code false} when the item is not a map.
	 */
	public boolean ensure(ItemStack item, World world) {
		ItemMeta meta = item.getItemMeta();
		if (!(meta instanceof MapMeta mapMeta)) {
			return false;
		}

		MapView existing = mapMeta.hasMapView() ? mapMeta.getMapView() : null;
		if (existing != null) {
			attach(existing);
			return true;
		}

		mapMeta.setMapView(viewFor(world));
		item.setItemMeta(mapMeta);
		return true;
	}

	/** Drops per-player renderer state (quit). */
	public void forget(UUID playerId) {
		renderer.forget(playerId);
	}
}
