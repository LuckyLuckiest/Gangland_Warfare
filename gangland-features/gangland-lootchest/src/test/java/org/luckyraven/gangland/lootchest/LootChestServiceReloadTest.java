package org.luckyraven.gangland.lootchest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.lootchest.LootChestService.OpenResult;
import org.luckyraven.gangland.lootchest.config.LootChestConfig;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootChestSession;
import org.luckyraven.gangland.lootchest.data.LootTable;
import org.luckyraven.gangland.lootchest.support.TestItemParsers;
import org.luckyraven.keystone.hologram.Hologram;
import org.luckyraven.keystone.hologram.HologramService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-951: what a {@code /glw reload} ({@link LootChestManager#onClear()}) leaves behind of the chests that were open.
 */
class LootChestServiceReloadTest {

	private MockedStatic<Bukkit> bukkit;
	private LootChestManager     service;

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
		      .thenAnswer(invocation -> mock(Inventory.class));
		bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

		HologramService holograms = mock(HologramService.class);
		when(holograms.createHologram(any(Location.class), any(String[].class))).thenReturn(mock(Hologram.class));
		service = new LootChestManager(mock(JavaPlugin.class), "glw", holograms, mock(RepositoryRegistry.class),
		                               TestItemParsers.materialOnly(), null, mock(InventoryService.class));
		service.setConfig(LootChestConfig.builder().tiers(Map.of()).lootTables(Map.of())
		                                 .openingSound("BLOCK_CHEST_OPEN").closingSound("BLOCK_CHEST_CLOSE").build());
		service.registerLootTable(new LootTable("common", "Common", List.of(), 1, 1, List.of(), Map.of()));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("T-951: after a reload with a chest open, the next opener gets a fresh inventory, not the pre-reload one")
	void onClear_withAChestOpen_dropsTheSharedInventory() {
		UUID   chestId = UUID.randomUUID();
		Player before  = player();
		Player after   = player();

		assertEquals(OpenResult.SUCCESS, service.tryOpenChest(before, chest(chestId)));

		service.onClear();

		assertEquals(OpenResult.SUCCESS, service.tryOpenChest(after, chest(chestId)));
		LootChestSession session = service.getActiveSession(after).orElseThrow();
		assertFalse(session.isUsingSharedInventory(),
		            "the post-reload opener must roll a fresh inventory instead of joining the pre-reload one");
	}

	@Test
	@DisplayName("T-951: a reload closes the inventory of the player who had the chest open, so it cannot be used unguarded")
	void onClear_closesTheViewersInventory() {
		Player viewer = player();
		service.tryOpenChest(viewer, chest(UUID.randomUUID()));

		service.onClear();

		verify(viewer).closeInventory();
	}

	private static Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}

	private static LootChestData chest(UUID id) {
		return LootChestData.builder().id(id).location(new Location(null, 1, 2, 3)).lootTableId("common")
		                    .inventorySize(27).displayName("Chest").build();
	}
}
