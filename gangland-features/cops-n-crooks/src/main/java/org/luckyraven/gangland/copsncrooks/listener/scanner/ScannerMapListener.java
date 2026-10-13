package org.luckyraven.gangland.copsncrooks.listener.scanner;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapView;
import org.luckyraven.gangland.copsncrooks.scanner.SearchZoneCache;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Feeds the Scanner Map: evasion events into the {@link SearchZoneCache}, held/swapped/joined map items into
 * {@link ScannerMapView#ensure}, quits out of both.
 *
 * @since 0.20.0
 */
@ListenerHandler
public class ScannerMapListener implements Listener {

	private final SearchZoneCache cache;
	private final ScannerMapView  mapView;
	private final CopsItems       items;
	private final CopLoader       copLoader;

	public ScannerMapListener(SearchZoneCache cache, ScannerMapView mapView, CopsItems items, CopLoader copLoader) {
		this.cache     = cache;
		this.mapView   = mapView;
		this.items     = items;
		this.copLoader = copLoader;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEvasion(WantedEvasionStateEvent event) {
		cache.record(event);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHeld(PlayerItemHeldEvent event) {
		if (!enabled()) {
			return;
		}
		Player player = event.getPlayer();
		attachIfMap(player, player.getInventory().getItem(event.getNewSlot()));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent event) {
		if (!enabled()) {
			return;
		}
		Player player = event.getPlayer();
		attachIfMap(player, event.getMainHandItem());
		attachIfMap(player, event.getOffHandItem());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		if (!enabled()) {
			return;
		}
		Player player = event.getPlayer();
		attachIfMap(player, player.getInventory().getItemInMainHand());
		attachIfMap(player, player.getInventory().getItemInOffHand());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		cache.evict(player.getUniqueId());
		mapView.forget(player.getUniqueId());
	}

	private boolean enabled() {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		ScannerMapSettings settings = provider == null ? ScannerMapSettings.DEFAULT : provider.getScannerMapSettings();
		return settings.enabled();
	}

	private void attachIfMap(Player player, @Nullable ItemStack item) {
		if (!items.isScannerMap(item)) {
			return;
		}
		World world = player.getWorld();
		if (world == null) {
			return;
		}
		mapView.ensure(item, world);
	}
}
