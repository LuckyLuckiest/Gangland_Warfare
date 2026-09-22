package org.luckyraven.gangland.lootchest.listener;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.lootchest.LootChestService;
import org.luckyraven.gangland.lootchest.SharedLootInventory;
import org.luckyraven.gangland.lootchest.data.LootChestSession;

import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the take-only deposit guard on {@link LootChestListener}'s click/drag handling.
 *
 * <p>Regression context: the migration off the deleted {@code InventoryHandler} framework (which cancelled every
 * click/shift-click deposit unless the slot was explicitly registered as draggable, and loot chests never
 * registered any slot as draggable — making chests take-only) onto the bare {@link SharedLootInventory} wrapper
 * dropped that gate entirely, silently widening loot chests into free shared storage. See
 * {@code brainstorming/decoupling-wave-2026-09-14/exec/G010/CUT-report.md}'s "Lootchest policy" section for the
 * review finding and ruling: loot chests must be take-only.
 *
 * <p>Every deposit-shaped {@link InventoryAction} onto the chest's top inventory (or a shift-click FROM the
 * player's own inventory INTO the chest) must be cancelled; every take-shaped action must be left untouched.
 */
class LootChestListenerTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// PICKUP_ALL / MOVE_TO_OTHER_INVENTORY current-item checks reach Material.isAir() -> Registry.
		BukkitRegistryFixture.install();
	}

	private LootChestService    manager;
	private LootChestListener   listener;
	private Player              player;
	private LootChestSession    session;
	private SharedLootInventory sharedInventory;
	private Inventory           topInventory;
	private InventoryView       view;

	@BeforeEach
	void setUp() {
		manager  = mock(LootChestService.class);
		listener = new LootChestListener(manager);

		player          = mock(Player.class);
		sharedInventory = mock(SharedLootInventory.class);
		topInventory    = mock(Inventory.class);
		view            = mock(InventoryView.class);

		when(topInventory.getHolder()).thenReturn(sharedInventory);
		when(topInventory.getSize()).thenReturn(27);
		when(view.getTopInventory()).thenReturn(topInventory);

		session = mock(LootChestSession.class);
		when(session.getState()).thenReturn(LootChestSession.SessionState.LOOTING);
		when(session.getInventory()).thenReturn(sharedInventory);
		when(session.getInventory().getSize()).thenReturn(27);

		when(manager.getActiveSession(player)).thenReturn(Optional.of(session));

		JavaPlugin      plugin    = mock(JavaPlugin.class);
		Server          server    = mock(Server.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(manager.getPlugin()).thenReturn(plugin);
		when(plugin.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(scheduler);
	}

	private InventoryClickEvent clickEvent(InventoryAction action, Inventory clickedInventory, int rawSlot) {
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getWhoClicked()).thenReturn(player);
		when(event.getView()).thenReturn(view);
		when(event.getAction()).thenReturn(action);
		when(event.getClickedInventory()).thenReturn(clickedInventory);
		when(event.getRawSlot()).thenReturn(rawSlot);
		return event;
	}

	@Test
	@DisplayName("PLACE_ALL onto a top slot is cancelled (deposit into the chest)")
	void placeAll_ontoTopSlot_isCancelled() {
		InventoryClickEvent event = clickEvent(InventoryAction.PLACE_ALL, topInventory, 3);

		listener.onInventoryClick(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("SWAP_WITH_CURSOR onto a top slot is cancelled (deposit into the chest)")
	void swapWithCursor_ontoTopSlot_isCancelled() {
		InventoryClickEvent event = clickEvent(InventoryAction.SWAP_WITH_CURSOR, topInventory, 5);

		listener.onInventoryClick(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("PLACE_ONE onto a top slot is cancelled (deposit into the chest)")
	void placeOne_ontoTopSlot_isCancelled() {
		InventoryClickEvent event = clickEvent(InventoryAction.PLACE_ONE, topInventory, 4);

		listener.onInventoryClick(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("PLACE_SOME onto a top slot is cancelled (deposit into the chest)")
	void placeSome_ontoTopSlot_isCancelled() {
		InventoryClickEvent event = clickEvent(InventoryAction.PLACE_SOME, topInventory, 6);

		listener.onInventoryClick(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("HOTBAR_SWAP onto a top slot is cancelled (deposit into the chest)")
	void hotbarSwap_ontoTopSlot_isCancelled() {
		InventoryClickEvent event = clickEvent(InventoryAction.HOTBAR_SWAP, topInventory, 2);

		listener.onInventoryClick(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("HOTBAR_MOVE_AND_READD onto a top slot is cancelled (deposit into the chest)")
	void hotbarMoveAndReadd_ontoTopSlot_isCancelled() {
		InventoryClickEvent event = clickEvent(InventoryAction.HOTBAR_MOVE_AND_READD, topInventory, 8);

		listener.onInventoryClick(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("MOVE_TO_OTHER_INVENTORY shift-clicked from the player's own inventory is cancelled (deposit)")
	void moveToOtherInventory_fromBottomInventory_isCancelled() {
		Inventory            bottomInventory = mock(Inventory.class);
		InventoryClickEvent  event           = clickEvent(InventoryAction.MOVE_TO_OTHER_INVENTORY, bottomInventory, 40);

		listener.onInventoryClick(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("MOVE_TO_OTHER_INVENTORY shift-clicked out of the chest into the player's inventory is a take, not cancelled")
	void moveToOtherInventory_fromTopInventory_isNotCancelled() {
		ItemStack            taken = itemStackOf(Material.DIAMOND);
		InventoryClickEvent  event = clickEvent(InventoryAction.MOVE_TO_OTHER_INVENTORY, topInventory, 3);
		when(event.getCurrentItem()).thenReturn(taken);

		listener.onInventoryClick(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("PICKUP_ALL from a top slot is never cancelled and still marks the item taken")
	void pickupAll_fromTopSlot_isNotCancelled_andMarksTaken() {
		ItemStack            picked = itemStackOf(Material.DIAMOND);
		InventoryClickEvent  event  = clickEvent(InventoryAction.PICKUP_ALL, topInventory, 3);
		when(event.getCurrentItem()).thenReturn(picked);

		listener.onInventoryClick(event);

		verify(event, never()).setCancelled(true);
		verify(session).markItemTaken();
	}

	@Test
	@DisplayName("A click with no active LOOTING session for the player is left completely alone")
	void noActiveSession_leavesEventAlone() {
		when(manager.getActiveSession(player)).thenReturn(Optional.empty());
		InventoryClickEvent event = clickEvent(InventoryAction.PLACE_ALL, topInventory, 3);

		listener.onInventoryClick(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("A drag distributing the cursor across a top slot is cancelled")
	void drag_touchingTopSlot_isCancelled() {
		InventoryDragEvent event = mock(InventoryDragEvent.class);
		when(event.getWhoClicked()).thenReturn(player);
		when(event.getView()).thenReturn(view);
		when(event.getRawSlots()).thenReturn(Set.of(2, 40));

		listener.onInventoryDrag(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("A drag entirely within the player's own inventory is left completely alone")
	void drag_onlyInBottomInventory_isNotCancelled() {
		InventoryDragEvent event = mock(InventoryDragEvent.class);
		when(event.getWhoClicked()).thenReturn(player);
		when(event.getView()).thenReturn(view);
		when(event.getRawSlots()).thenReturn(Set.of(30, 40));

		listener.onInventoryDrag(event);

		verify(event, never()).setCancelled(true);
	}

	private static ItemStack itemStackOf(Material material) {
		ItemStack item = mock(ItemStack.class);
		when(item.getType()).thenReturn(material);
		return item;
	}

}
