package org.luckyraven.gangland.copsncrooks.listener.scanner;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapView;
import org.luckyraven.gangland.copsncrooks.scanner.SearchZoneCache;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane scanmap: {@link ScannerMapListener} re-attaches the renderer whenever a Scanner Map is held, swapped to a hand
 * or carried through a join (P-MAP-1, P-MAP-2), caches evasion events and evicts on quit.
 */
@DisplayName("ScannerMapListener")
class ScannerMapListenerTest {

	private SearchZoneCache cache;
	private ScannerMapView  mapView;
	private CopsItems       items;
	private CopLoader       copLoader;
	private CopConfigProvider provider;
	private ScannerMapListener listener;

	private World           world;
	private Player          player;
	private PlayerInventory inventory;
	private ItemStack       scannerMap;
	private ItemStack       sword;

	@BeforeEach
	void setUp() {
		cache     = new SearchZoneCache();
		mapView   = mock(ScannerMapView.class);
		items     = mock(CopsItems.class);
		copLoader = mock(CopLoader.class);
		provider  = mock(CopConfigProvider.class);
		when(copLoader.getLoadedProvider()).thenReturn(provider);
		when(provider.getScannerMapSettings()).thenReturn(ScannerMapSettings.DEFAULT);
		listener = new ScannerMapListener(cache, mapView, items, copLoader);

		world     = mock(World.class);
		player    = mock(Player.class);
		inventory = mock(PlayerInventory.class);
		scannerMap = mock(ItemStack.class);
		sword      = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getWorld()).thenReturn(world);
		when(player.getInventory()).thenReturn(inventory);
		when(items.isScannerMap(scannerMap)).thenReturn(true);
	}

	@Test
	@DisplayName("holding a scanner map re-attaches the renderer exactly once")
	void holdingMapReattachesRenderer() {
		when(inventory.getItem(3)).thenReturn(scannerMap);

		listener.onHeld(new PlayerItemHeldEvent(player, 0, 3));

		verify(mapView, times(1)).ensure(scannerMap, world);
	}

	@Test
	@DisplayName("holding anything else touches nothing")
	void holdingOtherItemIgnored() {
		when(inventory.getItem(3)).thenReturn(sword);
		listener.onHeld(new PlayerItemHeldEvent(player, 0, 3));

		when(inventory.getItem(4)).thenReturn(null);
		listener.onHeld(new PlayerItemHeldEvent(player, 0, 4));

		verify(mapView, never()).ensure(any(), any());
	}

	@Test
	@DisplayName("with Scanner_Map.Enable false nothing is attached")
	void disabledAttachesNothing() {
		when(provider.getScannerMapSettings()).thenReturn(new ScannerMapSettings(false, 1, 10, true, "n", List.of()));
		when(inventory.getItem(3)).thenReturn(scannerMap);

		listener.onHeld(new PlayerItemHeldEvent(player, 0, 3));

		verify(mapView, never()).ensure(any(), any());
	}

	@Test
	@DisplayName("before the first config load the shipped defaults apply (enabled)")
	void noProviderUsesDefaults() {
		when(copLoader.getLoadedProvider()).thenReturn(null);
		when(inventory.getItem(3)).thenReturn(scannerMap);

		listener.onHeld(new PlayerItemHeldEvent(player, 0, 3));

		verify(mapView).ensure(scannerMap, world);
	}

	@Test
	@DisplayName("swapping a scanner map into either hand attaches it")
	void swapHandsAttachesEitherHand() {
		listener.onSwap(new PlayerSwapHandItemsEvent(player, scannerMap, sword));
		verify(mapView, times(1)).ensure(scannerMap, world);

		listener.onSwap(new PlayerSwapHandItemsEvent(player, sword, scannerMap));
		verify(mapView, times(2)).ensure(scannerMap, world);
		verify(mapView, never()).ensure(sword, world);
	}

	@Test
	@DisplayName("joining while a scanner map is in either hand attaches it (restart safety)")
	void joinAttachesHeldMaps() {
		when(inventory.getItemInMainHand()).thenReturn(scannerMap);
		when(inventory.getItemInOffHand()).thenReturn(scannerMap);

		listener.onJoin(new PlayerJoinEvent(player, "joined"));

		verify(mapView, times(2)).ensure(scannerMap, world);
	}

	@Test
	@DisplayName("joining with empty hands is harmless")
	void joinWithEmptyHands() {
		when(inventory.getItemInMainHand()).thenReturn(null);
		when(inventory.getItemInOffHand()).thenReturn(null);

		listener.onJoin(new PlayerJoinEvent(player, "joined"));

		verify(mapView, never()).ensure(any(), any());
	}

	@Test
	@DisplayName("an evasion event is cached for its player")
	void evasionEventCached() {
		listener.onEvasion(new WantedEvasionStateEvent(player, EvasionState.SEARCHING, 3, 20,
		                                               new org.bukkit.Location(world, 1, 64, 1), 30));

		assertNotNull(cache.get(player.getUniqueId()));
		assertEquals(EvasionState.SEARCHING, cache.get(player.getUniqueId()).state());
	}

	@Test
	@DisplayName("quit removes the cache entry and the renderer state")
	void quitEvicts() {
		cache.record(new WantedEvasionStateEvent(player, EvasionState.SEARCHING, 3, 20,
		                                         new org.bukkit.Location(world, 1, 64, 1), 30));

		listener.onQuit(new PlayerQuitEvent(player, "left"));

		assertNull(cache.get(player.getUniqueId()));
		verify(mapView).forget(player.getUniqueId());
	}
}
