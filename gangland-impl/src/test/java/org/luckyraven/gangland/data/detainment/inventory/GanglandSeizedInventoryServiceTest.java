package org.luckyraven.gangland.data.detainment.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventory;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the {@code CJ-06} fix (issue #35, cops-detainment-jail.md Observation #6): {@code restore} used to drop the
 * cache entry and delete the database row <em>before</em> deserialising the blob, so a
 * {@code ClassNotFoundException} or a serialization-version mismatch destroyed the player's entire seized inventory
 * with no recovery path. After the fix the decode happens first and the two deletions only run once the items are
 * actually on the player.
 *
 * <p>The corrupt-blob case is seeded through {@code repository.loadAll()} (the constructor's warm-up), so it needs no
 * Bukkit serialization at all.
 */
@DisplayName("GanglandSeizedInventoryService.restore — a failed decode must not destroy the seizure")
class GanglandSeizedInventoryServiceTest {

	private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000005ee");

	@SuppressWarnings("unchecked")
	private final IRepository<SeizedInventory> repository = mock(IRepository.class);

	private Player          player;
	private PlayerInventory inventory;

	@BeforeEach
	void setUp() {
		player    = mock(Player.class);
		inventory = mock(PlayerInventory.class);

		when(player.getUniqueId()).thenReturn(PLAYER_ID);
		when(player.getName()).thenReturn("Inmate");
		when(player.getInventory()).thenReturn(inventory);
	}

	@Test
	@DisplayName("a corrupt blob keeps the cache entry so the items can still be recovered")
	void restore_corruptBlob_keepsCacheEntry() {
		SeizedInventory seized = new SeizedInventory(PLAYER_ID, "@@ not a serialized inventory @@", 1L);
		when(repository.loadAll()).thenReturn(List.of(seized));

		GanglandSeizedInventoryService service = new GanglandSeizedInventoryService(repository);

		assertFalse(service.restore(player), "a failed decode must report failure");
		assertTrue(service.has(PLAYER_ID), "the seizure must survive a failed restore");
		assertSame(seized, service.peek(PLAYER_ID));
	}

	@Test
	@DisplayName("a corrupt blob leaves the database row in place")
	void restore_corruptBlob_doesNotDeleteTheRow() {
		when(repository.loadAll()).thenReturn(
				List.of(new SeizedInventory(PLAYER_ID, "@@ not a serialized inventory @@", 1L)));

		GanglandSeizedInventoryService service = new GanglandSeizedInventoryService(repository);
		service.restore(player);

		verify(repository, never()).delete(any());
	}

	@Test
	@DisplayName("a corrupt blob never half-writes the player's inventory")
	void restore_corruptBlob_doesNotTouchThePlayerInventory() {
		when(repository.loadAll()).thenReturn(
				List.of(new SeizedInventory(PLAYER_ID, "@@ not a serialized inventory @@", 1L)));

		GanglandSeizedInventoryService service = new GanglandSeizedInventoryService(repository);
		service.restore(player);

		verify(inventory, never()).setContents(any());
		verify(player, never()).updateInventory();
	}

	@Test
	@DisplayName("a healthy seizure still round-trips and is cleared afterwards")
	void snapshotThenRestore_roundTripsAndClears() {
		when(repository.loadAll()).thenReturn(List.of());
		when(inventory.getContents()).thenReturn(new ItemStack[]{null, null});
		when(inventory.getArmorContents()).thenReturn(new ItemStack[]{null});
		when(inventory.getItemInOffHand()).thenReturn(null);

		GanglandSeizedInventoryService service = new GanglandSeizedInventoryService(repository);
		service.snapshot(player);

		assertNotNull(service.peek(PLAYER_ID), "snapshot must cache the serialized blob");

		assertTrue(service.restore(player));
		assertFalse(service.has(PLAYER_ID), "a successful restore clears the cache");
		verify(repository, times(1)).delete(any());
		verify(player).updateInventory();
	}

	@Test
	@DisplayName("restoring a player with nothing seized is a no-op")
	void restore_nothingSeized_returnsFalse() {
		when(repository.loadAll()).thenReturn(List.of());

		GanglandSeizedInventoryService service = new GanglandSeizedInventoryService(repository);

		assertFalse(service.restore(player));
		verify(repository, never()).delete(any());
	}
}
