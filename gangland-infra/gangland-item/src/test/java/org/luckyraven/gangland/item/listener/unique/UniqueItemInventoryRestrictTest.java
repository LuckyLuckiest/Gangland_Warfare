package org.luckyraven.gangland.item.listener.unique;

import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.item.unique.UniqueItemKeys;
import org.luckyraven.keystone.item.nbt.NbtBridge;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterisation of {@link UniqueItemInventoryRestrict}: how each of its three handlers resolves a stack to a
 * registered {@link UniqueItem} (the {@code uniqueItem} NBT tag looked up in the registry) and what it then does -
 * cancel a click for a non-movable item, cancel a drop for a non-droppable one, strip non-Drop_On_Death items from
 * the death drops (every unique item when keepInventory is on, without consulting the registry).
 */
@DisplayName("UniqueItemInventoryRestrict - click, death-drop and drop restrictions")
class UniqueItemInventoryRestrictTest {

	private PerStackNbtAccessor         accessor;
	private UniqueItemRegistry          registry;
	private UniqueItemInventoryRestrict listener;

	@BeforeEach
	void setUp() {
		accessor = new PerStackNbtAccessor();
		NbtBridge.install(accessor);

		registry = mock(UniqueItemRegistry.class);
		listener = new UniqueItemInventoryRestrict(registry);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static UniqueItem.UniqueItemBuilder builder(String key) {
		return UniqueItem.builder()
		                 .uniqueItem(key).material(Material.IRON_INGOT).customModelData(0).name(key)
		                 .addOnJoin(false).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false)
		                 .addToInventory(true);
	}

	private void register(UniqueItem item) {
		when(registry.getUniqueItem(item.getUniqueItem())).thenReturn(item);
	}

	private ItemStack tagged(String key) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);
		when(stack.getAmount()).thenReturn(1);
		accessor.put(stack, UniqueItemKeys.UNIQUE_ITEM_KEY, key);
		return stack;
	}

	private ItemStack plain() {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(Material.DIRT);
		when(stack.getAmount()).thenReturn(1);
		return stack;
	}

	// ---------------------------------------------------------------- InventoryClickEvent

	private PlayerInventory ownInventory;

	private InventoryClickEvent click(ItemStack current, ItemStack cursor) {
		Player player = mock(Player.class);
		ownInventory = mock(PlayerInventory.class);
		when(ownInventory.getType()).thenReturn(InventoryType.PLAYER);
		when(player.getInventory()).thenReturn(ownInventory);

		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getClickedInventory()).thenReturn(ownInventory);
		when(event.getWhoClicked()).thenReturn(player);
		when(event.getCurrentItem()).thenReturn(current);
		when(event.getCursor()).thenReturn(cursor);
		return event;
	}

	@Test
	@DisplayName("click: a registered non-movable unique item in the player inventory cancels the click")
	void click_nonMovable_cancels() {
		register(builder("phone").movable(false).build());
		InventoryClickEvent event = click(tagged("phone"), null);

		listener.onUniqueItemInventoryClick(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("click: a movable unique item is left alone")
	void click_movable_isAllowed() {
		register(builder("phone").movable(true).build());
		InventoryClickEvent event = click(tagged("phone"), null);

		listener.onUniqueItemInventoryClick(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("click: a unique tag the registry does not know is left alone")
	void click_unregistered_isAllowed() {
		InventoryClickEvent event = click(tagged("ghost"), null);

		listener.onUniqueItemInventoryClick(event);

		verify(registry).getUniqueItem("ghost");
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("click: a stack without the unique tag never reaches the registry")
	void click_plainItem_neverQueriesRegistry() {
		InventoryClickEvent event = click(plain(), null);

		listener.onUniqueItemInventoryClick(event);

		verifyNoInteractions(registry);
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("click: with no current item the cursor stack is checked instead")
	void click_cursorFallback_cancels() {
		register(builder("phone").movable(false).build());
		InventoryClickEvent event = click(null, tagged("phone"));

		listener.onUniqueItemInventoryClick(event);

		verify(event).setCancelled(true);
	}

	// ---------------------------------------------------------------- PlayerDeathEvent

	private PlayerDeathEvent death(boolean keepInventory, List<ItemStack> drops) {
		World world = mock(World.class);
		when(world.getGameRuleValue(GameRule.KEEP_INVENTORY)).thenReturn(keepInventory);
		Player entity = mock(Player.class);
		when(entity.getWorld()).thenReturn(world);

		PlayerDeathEvent event = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(entity);
		when(event.getDrops()).thenReturn(drops);
		return event;
	}

	@Test
	@DisplayName("death drop: a unique item that is not Drop_On_Death is removed; the rest of the drops stay in order")
	void deathDrop_removesOnlyNonDropOnDeathUniqueItems() {
		register(builder("keepsake").dropOnDeath(false).build());
		register(builder("loot").dropOnDeath(true).build());
		ItemStack keepsake = tagged("keepsake");
		ItemStack loot     = tagged("loot");
		ItemStack ghost    = tagged("ghost"); // tagged but unknown to the registry
		ItemStack dirt     = plain();
		List<ItemStack> drops = new ArrayList<>(List.of(dirt, keepsake, loot, ghost));

		listener.onUniqueItemDeathDrop(death(false, drops));

		assertEquals(List.of(dirt, loot, ghost), drops);
		verify(registry).getUniqueItem("keepsake");
		verify(registry).getUniqueItem("loot");
		verify(registry).getUniqueItem("ghost");
	}

	@Test
	@DisplayName("death drop: with keepInventory every unique item is removed without consulting the registry")
	void deathDrop_keepInventory_removesEveryUniqueItem() {
		ItemStack loot = tagged("loot");
		ItemStack dirt = plain();
		List<ItemStack> drops = new ArrayList<>(List.of(loot, dirt));

		listener.onUniqueItemDeathDrop(death(true, drops));

		assertEquals(List.of(dirt), drops);
		verifyNoInteractions(registry);
	}

	// ---------------------------------------------------------------- PlayerDropItemEvent

	private PlayerDropItemEvent drop(ItemStack stack) {
		Item entity = mock(Item.class);
		when(entity.getItemStack()).thenReturn(stack);
		PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
		when(event.getItemDrop()).thenReturn(entity);
		return event;
	}

	@Test
	@DisplayName("drop: a registered non-droppable unique item cancels the drop")
	void drop_nonDroppable_cancels() {
		register(builder("phone").droppable(false).build());
		PlayerDropItemEvent event = drop(tagged("phone"));

		listener.onUniqueItemDrop(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("drop: a droppable unique item is left alone")
	void drop_droppable_isAllowed() {
		register(builder("phone").droppable(true).build());
		PlayerDropItemEvent event = drop(tagged("phone"));

		listener.onUniqueItemDrop(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("drop: a unique tag the registry does not know is left alone")
	void drop_unregistered_isAllowed() {
		PlayerDropItemEvent event = drop(tagged("ghost"));

		listener.onUniqueItemDrop(event);

		verify(registry).getUniqueItem("ghost");
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("drop: a stack without the unique tag never reaches the registry")
	void drop_plainItem_neverQueriesRegistry() {
		PlayerDropItemEvent event = drop(plain());

		listener.onUniqueItemDrop(event);

		verifyNoInteractions(registry);
		verify(event, never()).setCancelled(true);
	}

}
