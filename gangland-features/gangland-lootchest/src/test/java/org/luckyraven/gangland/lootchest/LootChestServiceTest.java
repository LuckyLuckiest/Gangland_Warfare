package org.luckyraven.gangland.lootchest;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootTable;
import org.luckyraven.gangland.lootchest.item.LootItemReference;
import org.luckyraven.gangland.lootchest.support.TestItemParsers;
import org.luckyraven.keystone.hologram.Hologram;
import org.luckyraven.keystone.hologram.HologramService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LootChestServiceTest {

	@Test
	@DisplayName("a looted chest whose cooldown ran out while the server was down comes back available")
	void registerChest_expiredCooldown_respawnsAndShowsHologram() {
		HologramService holograms = mock(HologramService.class);
		when(holograms.createHologram(any(Location.class), any(String[].class))).thenReturn(mock(Hologram.class));
		LootChestManager service = new LootChestManager(mock(JavaPlugin.class), "", holograms,
		                                                mock(RepositoryRegistry.class), null, null,
		                                                mock(InventoryService.class));

		LootChestData chest = LootChestData.builder()
		                                   .id(UUID.randomUUID())
		                                   .location(new Location(null, 1, 2, 3))
		                                   .lootTableId("common")
		                                   .respawnTime(300)
		                                   .isLooted(true)
		                                   .cooldownEndTime(System.currentTimeMillis() - 1_000)
		                                   .build();

		service.registerChest(chest);

		assertFalse(chest.isLooted(), "an expired cooldown must reset the looted flag");
		verify(holograms).createHologram(any(Location.class), any(String[].class));
	}

	@Test
	@DisplayName("an item string that does not resolve is reported with its table and entry id; valid ones are not")
	void findUnresolvedItems_namesOnlyTheBadEntry() {
		LootChestManager service = new LootChestManager(mock(JavaPlugin.class), "", mock(HologramService.class),
		                                                mock(RepositoryRegistry.class), TestItemParsers.materialOnly(),
		                                                null, mock(InventoryService.class));
		service.registerLootTable(new LootTable("common", "Common", List.of(entry("stone", "STONE"),
		                                                                    entry("typo", "bogus:thing")),
		                                        1, 1, List.of(), Map.of()));

		List<String> unresolved = service.findUnresolvedItems();

		assertEquals(1, unresolved.size(), unresolved.toString());
		assertTrue(unresolved.get(0).contains("common") && unresolved.get(0).contains("typo")
		           && unresolved.get(0).contains("bogus:thing"), unresolved.get(0));
	}

	private static LootItemReference entry(String id, String itemString) {
		return LootItemReference.builder().id(id).itemString(itemString).rarity(LootItemReference.Rarity.COMMON)
		                        .minAmount(1).maxAmount(1).weight(1).build();
	}
}
