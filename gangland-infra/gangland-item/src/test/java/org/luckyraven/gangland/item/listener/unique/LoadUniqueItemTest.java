package org.luckyraven.gangland.item.listener.unique;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LoadUniqueItem}.
 *
 * <p>Regression net for IT-02 (Observation #2, items-unique.md): {@code removeItem} had the same inversion as
 * {@code UniqueItemUtil.hasUniqueItem} — {@code if (uniqueItem.compareTo(contents[i]) == 0) continue;} then
 * {@code inventory.setItem(i, null)} — so it nulled the first slot that did <em>not</em> match the target item,
 * silently destroying an arbitrary inventory slot when a player was downed while holding a droppable unique
 * item. It now clears only stacks that {@code UniqueItem.matches}.
 *
 * <p>Also pins the join/respawn per-item skip gates (Add_On_Join / Add_To_Inventory / Add_On_Respawn) and the
 * async-event -> main-thread hop in {@code onJoinGiveItem} (W7, items-unique.md), using {@link BukkitStatics} so
 * the scheduler hop is deterministic.
 */
@DisplayName("LoadUniqueItem — join/respawn granting and downed-state removal")
class LoadUniqueItemTest {

	private PerStackNbtAccessor  accessor;
	private UniqueItemRegistry   registry;
	private JavaPlugin           plugin;
	private LoadUniqueItem       listener;

	@BeforeEach
	void setUp() {
		accessor = new PerStackNbtAccessor();
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
	@DisplayName("IT-02: onPlayerDowned removes only the target unique item, leaving a plain item and a different "
			+ "unique item of the same material untouched")
	void onPlayerDowned_removesOnlyTheTargetUniqueItem() {
		UniqueItem phone = droppablePhone();
		when(registry.getUniqueItems()).thenReturn(Map.of("phone", phone));

		// Slot 0: an unrelated plain item. Before the fix this was the slot the loop nulled, because it "did not
		// match" — the inverted branch destroyed an arbitrary inventory slot.
		ItemStack plainItem = mock(ItemStack.class);
		when(plainItem.getType()).thenReturn(Material.DIRT);

		// Slot 1: the actual phone — the only stack that must be removed.
		ItemStack builtPhone = taggedStack(Material.IRON_INGOT, "phone");

		// Slot 2: a different unique item of the same material. It must survive; the two-slot fixture this test
		// replaced could not tell "removed the right one" from "removed the first non-match".
		ItemStack lockpick = taggedStack(Material.IRON_INGOT, "lockpick");

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getContents()).thenReturn(new ItemStack[]{plainItem, builtPhone, lockpick});

		listener.onPlayerDowned(new PlayerDownedEvent(player));

		verify(inventory, never()).setItem(eq(0), any());
		verify(inventory).setItem(eq(1), isNull());
		verify(inventory, never()).setItem(eq(2), any());
	}

	@Test
	@DisplayName("IT-02: onPlayerDowned does nothing when the player does not hold the droppable unique item")
	void onPlayerDowned_playerDoesNotHoldItem_touchesNothing() {
		UniqueItem phone = droppablePhone();
		when(registry.getUniqueItems()).thenReturn(Map.of("phone", phone));

		ItemStack plainItem = mock(ItemStack.class);
		when(plainItem.getType()).thenReturn(Material.DIRT);

		ItemStack lockpick = taggedStack(Material.IRON_INGOT, "lockpick");

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getContents()).thenReturn(new ItemStack[]{plainItem, lockpick});

		listener.onPlayerDowned(new PlayerDownedEvent(player));

		verify(inventory, never()).setItem(anyInt(), any());
	}

	@Test
	@DisplayName("IT-02: with Allow_Duplicates true, onPlayerDowned removes every copy of the target item only")
	void onPlayerDowned_allowDuplicates_removesEveryCopyOfTheTargetOnly() {
		UniqueItem phone = UniqueItem.builder()
				.uniqueItem("phone").material(Material.IRON_INGOT).customModelData(0).name("&6Phone")
				.addOnJoin(false).addOnRespawn(false).dropOnDeath(false).allowDuplicates(true).addToInventory(true)
				.droppable(true)
				.build();
		when(registry.getUniqueItems()).thenReturn(Map.of("phone", phone));

		ItemStack firstPhone  = taggedStack(Material.IRON_INGOT, "phone");
		ItemStack lockpick    = taggedStack(Material.IRON_INGOT, "lockpick");
		ItemStack secondPhone = taggedStack(Material.IRON_INGOT, "phone");

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getContents()).thenReturn(new ItemStack[]{firstPhone, lockpick, secondPhone});

		listener.onPlayerDowned(new PlayerDownedEvent(player));

		verify(inventory).setItem(eq(0), isNull());
		verify(inventory, never()).setItem(eq(1), any());
		verify(inventory).setItem(eq(2), isNull());
	}

	private static UniqueItem droppablePhone() {
		return UniqueItem.builder()
				.uniqueItem("phone").material(Material.IRON_INGOT).customModelData(0).name("&6Phone")
				.addOnJoin(false).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false).addToInventory(true)
				.droppable(true)
				.build();
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

	private ItemStack taggedStack(Material material, String uniqueKey) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(material);
		accessor.put(stack, UniqueItemKeys.UNIQUE_ITEM_KEY, uniqueKey);
		return stack;
	}

	private org.bukkit.Location mockLocation() {
		org.bukkit.Location location = mock(org.bukkit.Location.class);
		return location;
	}

}
