package org.luckyraven.gangland.lootchest;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.keystone.hologram.Hologram;
import org.luckyraven.keystone.hologram.HologramService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
