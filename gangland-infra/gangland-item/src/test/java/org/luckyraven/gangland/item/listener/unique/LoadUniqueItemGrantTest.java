package org.luckyraven.gangland.item.listener.unique;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.downed.PlayerUndownedEvent;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.event.PlayerItemInitEvent;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.item.unique.UniqueItemKeys;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterisation of the per-item gates of {@link LoadUniqueItem}'s granting loops (join and respawn/undowned): the
 * join/respawn flag, Add_To_Inventory, the already-held-and-no-duplicates skip, and where the registry is read. A grant
 * that passes every gate reaches {@code UniqueItem.addItemToInventory}, which asks the inventory for its size first;
 * with the size unstubbed (0) it then stops, so {@code getSize()} counts how many items reached placement.
 */
@DisplayName("LoadUniqueItem - grant gates of the join and respawn loops")
class LoadUniqueItemGrantTest {

	private PerStackNbtAccessor accessor;
	private UniqueItemRegistry  registry;
	private JavaPlugin          plugin;
	private LoadUniqueItem      listener;
	private Player              player;
	private PlayerInventory     inventory;

	@BeforeEach
	void setUp() {
		accessor = new PerStackNbtAccessor();
		NbtBridge.install(accessor);

		registry  = mock(UniqueItemRegistry.class);
		plugin    = mock(JavaPlugin.class);
		listener  = new LoadUniqueItem(registry, plugin);
		player    = mock(Player.class);
		inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static UniqueItem.UniqueItemBuilder builder(String key) {
		return UniqueItem.builder()
		                 .uniqueItem(key).material(Material.IRON_INGOT).customModelData(0).name(key).inventorySlot(0)
		                 .addOnJoin(true).addOnRespawn(true).dropOnDeath(false).allowDuplicates(false)
		                 .addToInventory(true);
	}

	private ItemStack held(String key) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);
		accessor.put(stack, UniqueItemKeys.UNIQUE_ITEM_KEY, key);
		return stack;
	}

	private void playerHolds(String... keys) {
		ItemStack[] contents = new ItemStack[keys.length];
		for (int i = 0; i < keys.length; i++) contents[i] = held(keys[i]);
		when(inventory.getContents()).thenReturn(contents);
	}

	private void respawn() {
		listener.onPlayerRespawn(new PlayerRespawnEvent(player, mock(Location.class), false));
	}

	private void join() {
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));
	}

	// ---------------------------------------------------------------- respawn / undowned

	@Test
	@DisplayName("respawn: an Add_On_Respawn item with Add_To_Inventory false is skipped without touching the player")
	void respawn_addToInventoryFalse_isSkipped() {
		UniqueItem noSlot = builder("gasoline").addToInventory(false).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("gasoline", noSlot));

		respawn();

		verifyNoInteractions(player);
	}

	@Test
	@DisplayName("respawn: an item the player already holds is skipped when duplicates are not allowed")
	void respawn_alreadyHeld_noDuplicates_isSkipped() {
		UniqueItem key = builder("legendary_key").allowDuplicates(false).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("legendary_key", key));
		playerHolds("legendary_key");

		respawn();

		verify(inventory, never()).getSize();
	}

	@Test
	@DisplayName("respawn: an item the player already holds still reaches placement when duplicates are allowed")
	void respawn_alreadyHeld_duplicatesAllowed_reachesPlacement() {
		UniqueItem key = builder("legendary_key").allowDuplicates(true).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("legendary_key", key));
		playerHolds("legendary_key");

		respawn();

		verify(inventory, times(1)).getSize();
	}

	@Test
	@DisplayName("undowned: the same gates apply as on respawn")
	void undowned_sameGates() {
		UniqueItem held   = builder("held_key").allowDuplicates(false).build();
		UniqueItem notHeld = builder("new_key").allowDuplicates(false).build();
		UniqueItem noRespawn = builder("quiet_key").addOnRespawn(false).build();
		Map<String, UniqueItem> items = new LinkedHashMap<>();
		items.put("held_key", held);
		items.put("new_key", notHeld);
		items.put("quiet_key", noRespawn);
		when(registry.getUniqueItems()).thenReturn(items);
		playerHolds("held_key");

		listener.onPlayerUndowned(new PlayerUndownedEvent(player));

		verify(inventory, times(1)).getSize(); // only new_key passed every gate
		verify(registry, times(1)).getUniqueItems();
	}

	// ---------------------------------------------------------------- join

	@Test
	@DisplayName("join: an item the player already holds is skipped when duplicates are not allowed")
	void join_alreadyHeld_noDuplicates_isSkipped() {
		UniqueItem key = builder("legendary_key").allowDuplicates(false).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("legendary_key", key));
		playerHolds("legendary_key");

		join();

		verify(inventory, never()).getSize();
	}

	@Test
	@DisplayName("join: an item the player already holds still reaches placement when duplicates are allowed")
	void join_alreadyHeld_duplicatesAllowed_reachesPlacement() {
		UniqueItem key = builder("legendary_key").allowDuplicates(true).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("legendary_key", key));
		playerHolds("legendary_key");

		join();

		verify(inventory, times(1)).getSize();
	}

	@Test
	@DisplayName("join: the Add_On_Join flag, not Add_On_Respawn, gates the loop; every passing item reaches placement")
	void join_usesTheJoinFlag() {
		UniqueItem joinOnly    = builder("join_key").addOnJoin(true).addOnRespawn(false).build();
		UniqueItem joinToo     = builder("join_too_key").addOnJoin(true).addOnRespawn(false).build();
		UniqueItem respawnOnly = builder("respawn_key").addOnJoin(false).addOnRespawn(true).build();
		UniqueItem both        = builder("both_key").addOnJoin(true).addOnRespawn(true).build();
		Map<String, UniqueItem> items = new LinkedHashMap<>();
		items.put("join_key", joinOnly);
		items.put("join_too_key", joinToo);
		items.put("respawn_key", respawnOnly);
		items.put("both_key", both);
		when(registry.getUniqueItems()).thenReturn(items);
		playerHolds();

		join();

		verify(inventory, times(3)).getSize();
	}

	@Test
	@DisplayName("respawn: the Add_On_Respawn flag, not Add_On_Join, gates the loop; every passing item reaches placement")
	void respawn_usesTheRespawnFlag() {
		UniqueItem joinOnly    = builder("join_key").addOnJoin(true).addOnRespawn(false).build();
		UniqueItem joinToo     = builder("join_too_key").addOnJoin(true).addOnRespawn(false).build();
		UniqueItem respawnOnly = builder("respawn_key").addOnJoin(false).addOnRespawn(true).build();
		UniqueItem both        = builder("both_key").addOnJoin(true).addOnRespawn(true).build();
		Map<String, UniqueItem> items = new LinkedHashMap<>();
		items.put("join_key", joinOnly);
		items.put("join_too_key", joinToo);
		items.put("respawn_key", respawnOnly);
		items.put("both_key", both);
		when(registry.getUniqueItems()).thenReturn(items);
		playerHolds();

		respawn();

		verify(inventory, times(2)).getSize();
	}

	@Test
	@DisplayName("async join: the registry is read only when the scheduled runnable runs, not before the hop")
	void join_async_readsRegistryInsideTheRunnable() {
		when(registry.getUniqueItems()).thenReturn(Map.of());

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			BukkitScheduler scheduler = bukkit.scheduler();
			// hold the submitted runnable instead of running it inline
			doReturn(null).when(scheduler).runTask(eq(plugin), any(Runnable.class));

			listener.onJoinGiveItem(new PlayerItemInitEvent(player, true));

			verify(registry, never()).getUniqueItems();

			ArgumentCaptor<Runnable> submitted = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(plugin), submitted.capture());
			assertNotNull(submitted.getValue());
			submitted.getValue().run();

			verify(registry, times(1)).getUniqueItems();
		}
	}

}
