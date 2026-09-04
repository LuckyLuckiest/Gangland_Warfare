package org.luckyraven.gangland.item.listener.unique;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.downed.PlayerUndownedEvent;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.event.PlayerItemInitEvent;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.item.unique.UniqueItemKeys;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LoadUniqueItem}.
 *
 * <p>Pins Observation #2 (items-unique.md): {@code removeItem} has the same inversion as
 * {@code UniqueItemUtil.hasUniqueItem} — {@code if (uniqueItem.compareTo(contents[i]) == 0) continue;} then
 * {@code inventory.setItem(i, null)} — so it nulls the first slot that does <em>not</em> match the target item,
 * i.e. it can silently destroy an arbitrary inventory slot when a player is downed while holding a droppable
 * unique item.
 *
 * <p>Also pins the join/respawn per-item skip gates (Add_On_Join / Add_To_Inventory / Add_On_Respawn) and the
 * async-event -> main-thread hop in {@code onJoinGiveItem} (W7, items-unique.md), using {@link BukkitStatics} so
 * the scheduler hop is deterministic.
 */
@DisplayName("LoadUniqueItem — join/respawn granting and downed-state removal")
class LoadUniqueItemTest {

	private RecordingNbtAccessor accessor;
	private UniqueItemRegistry   registry;
	private JavaPlugin           plugin;
	private LoadUniqueItem       listener;

	@BeforeEach
	void setUp() {
		accessor = new RecordingNbtAccessor();
		NbtBridge.install(accessor);

		registry = mock(UniqueItemRegistry.class);
		plugin   = mock(JavaPlugin.class);
		listener = new LoadUniqueItem(registry, plugin);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static UniqueItem.UniqueItemBuilder baseBuilder(String key) {
		return UniqueItem.builder()
				.uniqueItem(key).material(Material.IRON_INGOT).customModelData(0).name(key)
				.addOnJoin(false).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false).addToInventory(false);
	}

	// -------------------------------------------------------------- onJoinGiveItem: per-item skip gates

	@Test
	@DisplayName("an item with Add_On_Join: false is skipped without ever touching the player's inventory")
	void onJoinGiveItem_addOnJoinFalse_neverTouchesInventory() {
		UniqueItem notOnJoin = baseBuilder("lockpick").addOnJoin(false).addToInventory(true).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("lockpick", notOnJoin));

		Player player = mock(Player.class);
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));

		verifyNoInteractions(player);
	}

	@Test
	@DisplayName("an item with Add_On_Join: true but Slot: -1 (addToInventory=false) is also skipped")
	void onJoinGiveItem_addToInventoryFalse_isSkipped() {
		UniqueItem noSlot = baseBuilder("gasoline").addOnJoin(true).addToInventory(false).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("gasoline", noSlot));

		Player player = mock(Player.class);
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));

		verifyNoInteractions(player);
	}

	@Test
	@DisplayName("a synchronous PlayerItemInitEvent runs the grant logic inline, never touching the scheduler")
	void onJoinGiveItem_syncEvent_runsInline() {
		when(registry.getUniqueItems()).thenReturn(Map.of());
		Player player = mock(Player.class);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));

			verify(bukkit.scheduler(), never()).runTask(any(), any(Runnable.class));
		}
	}

	@Test
	@DisplayName("an async PlayerItemInitEvent hops the grant logic onto the main thread via the scheduler")
	void onJoinGiveItem_asyncEvent_hopsThroughScheduler() {
		when(registry.getUniqueItems()).thenReturn(Map.of());
		Player player = mock(Player.class);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			listener.onJoinGiveItem(new PlayerItemInitEvent(player, true));

			verify(bukkit.scheduler()).runTask(eq(plugin), any(Runnable.class));
			// BukkitStatics runs the submitted Runnable inline, so the map lookup still happened despite the hop.
			verify(registry, atLeastOnce()).getUniqueItems();
		}
	}

	// -------------------------------------------------------------- onPlayerRespawn / onPlayerUndowned skip gates

	@Test
	@DisplayName("onPlayerRespawn skips an item whose Add_On_Respawn is false")
	void onPlayerRespawn_addOnRespawnFalse_isSkipped() {
		UniqueItem notOnRespawn = baseBuilder("epic_key").addOnRespawn(false).addToInventory(true).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("epic_key", notOnRespawn));

		Player player = mock(Player.class);
		listener.onPlayerRespawn(new org.bukkit.event.player.PlayerRespawnEvent(player, mockLocation(), false));

		verifyNoInteractions(player);
	}

	@Test
	@DisplayName("onPlayerUndowned reaches the inventory-placement gate for an Add_On_Respawn item the player "
			+ "does not already hold")
	void onPlayerUndowned_passesGatesForConfiguredItem() {
		UniqueItem regrant = baseBuilder("legendary_key").addOnRespawn(true).addToInventory(true).inventorySlot(0)
				.build();
		when(registry.getUniqueItems()).thenReturn(Map.of("legendary_key", regrant));

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getContents()).thenReturn(new ItemStack[0]); // empty inventory -> hasUniqueItem() is false

		listener.onPlayerUndowned(new PlayerUndownedEvent(player));

		// addItemToInventory -> addItem checks inventorySlot >= inventory.getSize() first; with getSize()
		// unstubbed (defaulting to 0) that check fails closed, so we never reach createItem -> buildItem(player)
		// (which would need a live ItemFactory — see UniqueItemUtilTest javadoc). This only proves the two
		// per-item skip gates (Add_On_Respawn, hasUniqueItem-and-not-allowDuplicates) were both passed.
		verify(inventory).getSize();
	}

	// -------------------------------------------------------------- onPlayerDowned / removeItem inversion (#2)

	@Test
	@DisplayName("Observations #2 + #3: onPlayerDowned preserves a plain (non-unique) item and instead destroys "
			+ "the genuine unique item, because compareTo() never equals 0 against a colour-translated display "
			+ "name")
	void onPlayerDowned_preservesPlainItem_destroysGenuineUniqueItem() {
		UniqueItem phone = UniqueItem.builder()
				.uniqueItem("phone").material(Material.IRON_INGOT).customModelData(0).name("&6Phone")
				.addOnJoin(false).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false).addToInventory(true)
				.droppable(true)
				.build();
		when(registry.getUniqueItems()).thenReturn(Map.of("phone", phone));

		// Slot 0: an unrelated plain item — Mockito leaves getItemMeta() as null by default, so compareTo()
		// short-circuits to 0 via the "meta == null" branch and the buggy "continue on match" preserves it.
		ItemStack plainItem = mock(ItemStack.class);
		when(plainItem.getType()).thenReturn(Material.DIRT);

		// Slot 1: the actual, correctly-built phone (colour-translated display name) — its compareTo() against
		// itself is never 0 (Observation #3), so the loop treats it as "not a match" and nulls it. This same
		// mismatch is also what makes hasUniqueItem(player, phone) report true and let onPlayerDowned proceed.
		ItemStack builtPhone = uniqueStack(Material.IRON_INGOT, "&6Phone");

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getContents()).thenReturn(new ItemStack[]{plainItem, builtPhone});

		listener.onPlayerDowned(new PlayerDownedEvent(player));

		verify(inventory, never()).setItem(eq(0), any());
		verify(inventory).setItem(eq(1), isNull());
	}

	@Test
	@DisplayName("onPlayerDowned skips an item that is not Droppable, never scanning the inventory")
	void onPlayerDowned_notDroppable_isSkipped() {
		UniqueItem notDroppable = baseBuilder("phone").droppable(false).build();
		when(registry.getUniqueItems()).thenReturn(Map.of("phone", notDroppable));

		Player player = mock(Player.class);
		listener.onPlayerDowned(new PlayerDownedEvent(player));

		verifyNoInteractions(player);
	}

	// -------------------------------------------------------------- fixtures

	private ItemStack uniqueStack(Material material, String rawDisplayName) {
		ItemStack stack = mock(ItemStack.class);
		ItemMeta  meta  = mock(ItemMeta.class);
		when(stack.getType()).thenReturn(material);
		when(stack.getItemMeta()).thenReturn(meta);
		when(meta.getDisplayName()).thenReturn(ChatUtil.color(rawDisplayName));
		accessor.values.put(UniqueItemKeys.UNIQUE_ITEM_KEY, "shared-tag-not-discriminating");
		return stack;
	}

	private org.bukkit.Location mockLocation() {
		org.bukkit.Location location = mock(org.bukkit.Location.class);
		return location;
	}

}
