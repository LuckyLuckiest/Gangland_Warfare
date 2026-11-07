package org.luckyraven.gangland.item.listener.fuel;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.fuel.Fuel;
import org.luckyraven.gangland.item.fuel.FuelBar;
import org.luckyraven.gangland.item.fuel.FuelContract;
import org.luckyraven.gangland.item.fuel.FuelKey;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterisation of the refuel paths of {@link FuelRefuelListener}: world right-click ({@code onInteract}) and
 * inventory click with a fuel material ({@code onInventoryClick}). Pins today's outputs and side effects for the
 * success, already-full, wrong-material and unknown-fuel outcomes of every entry point, so a later change to the
 * private refuel helpers cannot alter them unnoticed.
 */
@DisplayName("FuelRefuelListener - refuel by material (world and inventory entry points)")
class FuelRefuelListenerRefuelTest {

	private static final String FUEL_KEY      = "gasoline";
	private static final String FULL_MESSAGE  = "&cFuel is already full!";
	private static final int    FUEL_PER_ITEM = 30;

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();

		// XMaterial parses the server version once, when its class first loads, and the fixture's server reports the
		// unparseable "test"; load it here against a parseable version so every test below sees a working XMaterial.
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.16.5-R0.1-SNAPSHOT");
			bukkit.when(Bukkit::getVersion).thenReturn("git-Spigot-test (MC: 1.16.5)");
			XMaterial.COAL.get();
		}
	}

	private PerStackNbtAccessor nbt;
	private FuelContract       fuelContract;
	private FuelRefuelListener listener;
	private Player             player;
	private InventoryView      view;
	private PlayerInventory    inventory;
	private Location           location;

	@BeforeEach
	void setUp() {
		nbt = new PerStackNbtAccessor();
		NbtBridge.install(nbt);

		fuelContract = mock(FuelContract.class);
		Fuel gasoline = Fuel.builder()
		                    .fuelKey(FUEL_KEY)
		                    .maxFuel(100)
		                    .displayName("Gasoline")
		                    .fuelMaterial(XMaterial.COAL)
		                    .fuelPerItem(FUEL_PER_ITEM)
		                    .build();
		when(fuelContract.getFuel(FUEL_KEY)).thenReturn(gasoline);

		listener  = new FuelRefuelListener(fuelContract);
		player    = mock(Player.class);
		view      = mock(InventoryView.class);
		inventory = mock(PlayerInventory.class);
		location  = mock(Location.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getInventory()).thenReturn(inventory);
		when(player.getLocation()).thenReturn(location);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private ItemStack fuelItem(int current, int max) {
		return fuelItem(FUEL_KEY, current, max);
	}

	private ItemStack fuelItem(String key, int current, int max) {
		ItemBuilder builder = new ItemBuilder(new ItemStack(Material.STICK));
		builder.addTag(FuelKey.FUEL_ID.getKey(), key);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), current);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), max);
		return builder.build();
	}

	private InventoryClickEvent clickEvent(ItemStack cursor, ItemStack clicked) {
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getWhoClicked()).thenReturn(player);
		when(event.getCursor()).thenReturn(cursor);
		when(event.getCurrentItem()).thenReturn(clicked);
		when(event.getView()).thenReturn(view);
		return event;
	}

	private PlayerInteractEvent interactEvent(ItemStack mainHand, ItemStack offHand) {
		PlayerInteractEvent event = mock(PlayerInteractEvent.class);
		when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
		when(event.getHand()).thenReturn(EquipmentSlot.HAND);
		when(event.getPlayer()).thenReturn(player);
		when(inventory.getItemInMainHand()).thenReturn(mainHand);
		when(inventory.getItemInOffHand()).thenReturn(offHand);
		return event;
	}

	private void verifyFeedback(MockedStatic<ActionBarManager> bar, int newFuel, int max) {
		verify(player).playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
		bar.verify(() -> ActionBarManager.send(player, FuelBar.render(newFuel, max)));
	}

	private void verifyNoFeedback(MockedStatic<ActionBarManager> bar) {
		verify(player, never()).playSound(any(Location.class), any(Sound.class), eq(0.5f), eq(1.2f));
		bar.verifyNoInteractions();
	}

	// ---------------------------------------------------------------- inventory click

	@Test
	@DisplayName("click: cursor material onto a fuel item consumes one material and writes the new fuel to the slot")
	void click_cursorMaterial_refuelsClickedItem() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelItem(10, 100);
		InventoryClickEvent event = clickEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);

			assertEquals(2, coal.getAmount());
			ArgumentCaptor<ItemStack> updated = ArgumentCaptor.forClass(ItemStack.class);
			verify(event).setCurrentItem(updated.capture());
			assertEquals(10 + FUEL_PER_ITEM, Fuel.getCurrentFuel(updated.getValue()));
			verify(event).setCancelled(true);
			verify(view, never()).setCursor(any());
			verifyFeedback(bar, 10 + FUEL_PER_ITEM, 100);
		}
	}

	@Test
	@DisplayName("click: cursor fuel item over a material writes the new fuel to the cursor and shrinks the clicked stack")
	void click_cursorFuel_refuelsCursor() {
		ItemStack fuel  = fuelItem(10, 100);
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		InventoryClickEvent event = clickEvent(fuel, coal);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);

			assertEquals(2, coal.getAmount());
			ArgumentCaptor<ItemStack> updated = ArgumentCaptor.forClass(ItemStack.class);
			verify(view).setCursor(updated.capture());
			assertEquals(10 + FUEL_PER_ITEM, Fuel.getCurrentFuel(updated.getValue()));
			verify(event).setCancelled(true);
			verify(event, never()).setCurrentItem(any());
			verifyFeedback(bar, 10 + FUEL_PER_ITEM, 100);
		}
	}

	@Test
	@DisplayName("click: the new fuel is clamped to the maximum")
	void click_nearlyFull_clampsToMax() {
		ItemStack coal  = new ItemStack(Material.COAL, 1);
		ItemStack fuel  = fuelItem(90, 100);
		InventoryClickEvent event = clickEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);

			assertEquals(0, coal.getAmount());
			ArgumentCaptor<ItemStack> updated = ArgumentCaptor.forClass(ItemStack.class);
			verify(event).setCurrentItem(updated.capture());
			assertEquals(100, Fuel.getCurrentFuel(updated.getValue()));
			verifyFeedback(bar, 100, 100);
		}
	}

	@Test
	@DisplayName("click: a full fuel item is refused with the full message, nothing consumed or cancelled")
	void click_full_refusesWithMessage() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelItem(100, 100);
		InventoryClickEvent event = clickEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);

			assertEquals(3, coal.getAmount());
			verify(event, never()).setCancelled(true);
			verify(event, never()).setCurrentItem(any());
			verify(view, never()).setCursor(any());
			verify(player, never()).playSound(any(Location.class), any(Sound.class), eq(0.5f), eq(1.2f));
			bar.verify(() -> ActionBarManager.send(player, FULL_MESSAGE));
		}
	}

	@Test
	@DisplayName("click: a full fuel item on the cursor is refused the same way")
	void click_cursorFuelFull_refusesWithMessage() {
		ItemStack fuel  = fuelItem(100, 100);
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		InventoryClickEvent event = clickEvent(fuel, coal);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);

			assertEquals(3, coal.getAmount());
			verify(event, never()).setCancelled(true);
			verify(view, never()).setCursor(any());
			bar.verify(() -> ActionBarManager.send(player, FULL_MESSAGE));
		}
	}

	@Test
	@DisplayName("click: the wrong material does nothing, silently")
	void click_wrongMaterial_doesNothing() {
		ItemStack stone = new ItemStack(Material.STONE, 3);
		ItemStack fuel  = fuelItem(10, 100);
		InventoryClickEvent event = clickEvent(stone, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);

			assertEquals(3, stone.getAmount());
			verify(event, never()).setCancelled(true);
			verify(event, never()).setCurrentItem(any());
			verifyNoFeedback(bar);
		}
	}

	@Test
	@DisplayName("click: an unregistered fuel key does nothing, silently")
	void click_unknownFuel_doesNothing() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelItem("unregistered", 10, 100);
		InventoryClickEvent event = clickEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);

			assertEquals(3, coal.getAmount());
			verify(event, never()).setCancelled(true);
			verify(event, never()).setCurrentItem(any());
			verifyNoFeedback(bar);
		}
	}

	// ---------------------------------------------------------------- world interaction

	@Test
	@DisplayName("interact: material in the main hand and fuel in the off hand refuels the off hand")
	void interact_materialMainHand_refuelsOffHand() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelItem(10, 100);
		PlayerInteractEvent event = interactEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInteract(event);

			assertEquals(2, coal.getAmount());
			ArgumentCaptor<ItemStack> updated = ArgumentCaptor.forClass(ItemStack.class);
			verify(inventory).setItemInOffHand(updated.capture());
			assertEquals(10 + FUEL_PER_ITEM, Fuel.getCurrentFuel(updated.getValue()));
			verify(inventory, never()).setItemInMainHand(any());
			verify(event).setCancelled(true);
			verifyFeedback(bar, 10 + FUEL_PER_ITEM, 100);
		}
	}

	@Test
	@DisplayName("interact: fuel in the main hand and material in the off hand refuels the main hand")
	void interact_fuelMainHand_refuelsMainHand() {
		ItemStack fuel  = fuelItem(10, 100);
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		PlayerInteractEvent event = interactEvent(fuel, coal);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInteract(event);

			assertEquals(2, coal.getAmount());
			ArgumentCaptor<ItemStack> updated = ArgumentCaptor.forClass(ItemStack.class);
			verify(inventory).setItemInMainHand(updated.capture());
			assertEquals(10 + FUEL_PER_ITEM, Fuel.getCurrentFuel(updated.getValue()));
			verify(inventory, never()).setItemInOffHand(any());
			verify(event).setCancelled(true);
			verifyFeedback(bar, 10 + FUEL_PER_ITEM, 100);
		}
	}

	@Test
	@DisplayName("interact: a full fuel item is refused with the full message, nothing consumed or cancelled")
	void interact_full_refusesWithMessage() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelItem(100, 100);
		PlayerInteractEvent event = interactEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInteract(event);

			assertEquals(3, coal.getAmount());
			verify(inventory, never()).setItemInOffHand(any());
			verify(inventory, never()).setItemInMainHand(any());
			verify(event, never()).setCancelled(true);
			bar.verify(() -> ActionBarManager.send(player, FULL_MESSAGE));
		}
	}

	@Test
	@DisplayName("interact: the wrong material does nothing, silently")
	void interact_wrongMaterial_doesNothing() {
		ItemStack stone = new ItemStack(Material.STONE, 3);
		ItemStack fuel  = fuelItem(10, 100);
		PlayerInteractEvent event = interactEvent(stone, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInteract(event);

			assertEquals(3, stone.getAmount());
			verify(inventory, never()).setItemInOffHand(any());
			verify(event, never()).setCancelled(true);
			verifyNoFeedback(bar);
		}
	}

	@Test
	@DisplayName("interact: an unregistered fuel key does nothing, silently")
	void interact_unknownFuel_doesNothing() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelItem("unregistered", 10, 100);
		PlayerInteractEvent event = interactEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInteract(event);

			assertEquals(3, coal.getAmount());
			verify(inventory, never()).setItemInOffHand(any());
			verify(event, never()).setCancelled(true);
			verifyNoFeedback(bar);
		}
	}
	@Test
	@DisplayName("T-948 interact: the off-hand copy of one right-click is ignored, so a click refuels once")
	void interact_offHandEvent_isIgnored() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelItem(10, 100);
		PlayerInteractEvent event = interactEvent(coal, fuel);
		when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInteract(event);

			assertEquals(3, coal.getAmount(), "the OFF_HAND event of the same click must not consume a second coal");
			verify(inventory, never()).setItemInOffHand(any());
			verify(inventory, never()).setItemInMainHand(any());
			verify(event, never()).setCancelled(true);
			verifyNoFeedback(bar);
		}
	}

	// ---------------------------------------------------------------- stacks of fuel items (GD-09)

	/** A stack of {@code amount} fuel items whose clones carry the tags too, as a real {@code ItemStack} clone does. */
	private ItemStack fuelStack(int amount, int current, int max) {
		ItemStack stack = new ItemStack(Material.STICK, amount) {
			@Override
			public ItemStack clone() {
				ItemStack copy = super.clone();
				for (FuelKey key : new FuelKey[]{FuelKey.FUEL_ID, FuelKey.FUEL_CURRENT, FuelKey.FUEL_MAX}) {
					nbt.put(copy, key.getKey(), nbt.get(this, key.getKey()));
				}
				return copy;
			}
		};
		ItemBuilder builder = new ItemBuilder(stack);
		builder.addTag(FuelKey.FUEL_ID.getKey(), FUEL_KEY);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), current);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), max);
		return builder.build();
	}



	/** Fuel in the hand stack plus every stack handed back to the inventory, counted per can. */
	private int totalFuel(ItemStack inSlot, ArgumentCaptor<ItemStack> added) {
		int total = inSlot.getAmount() * Fuel.getCurrentFuel(inSlot);
		for (ItemStack stack : added.getAllValues()) {
			total += stack.getAmount() * Fuel.getCurrentFuel(stack);
		}
		return total;
	}

	@Test
	@DisplayName("GD-09 click: one material refuels one can of a stack, the rest keep their level")
	void click_stackOfFuelItems_refuelsOnlyOneCan() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelStack(2, 10, 100);
		InventoryClickEvent event = clickEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);

			ArgumentCaptor<ItemStack> slot = ArgumentCaptor.forClass(ItemStack.class);
			verify(event).setCurrentItem(slot.capture());
			ArgumentCaptor<ItemStack> added = ArgumentCaptor.forClass(ItemStack.class);
			verify(inventory, atLeast(0)).addItem(added.capture());

			assertEquals(2 * 10 + FUEL_PER_ITEM, totalFuel(slot.getValue(), added),
			             "one coal adds one coal's fuel across the whole stack");
			assertEquals(1, slot.getValue().getAmount());
			assertEquals(10, Fuel.getCurrentFuel(slot.getValue()));
			assertEquals(10 + FUEL_PER_ITEM, Fuel.getCurrentFuel(added.getValue()));
		}
	}

	@Test
	@DisplayName("GD-09 interact: one material refuels one can of a stack in the hand, the rest keep their level")
	void interact_stackOfFuelItems_refuelsOnlyOneCan() {
		ItemStack coal  = new ItemStack(Material.COAL, 3);
		ItemStack fuel  = fuelStack(2, 10, 100);
		PlayerInteractEvent event = interactEvent(coal, fuel);

		try (MockedStatic<ActionBarManager> bar = mockStatic(ActionBarManager.class)) {
			listener.onInteract(event);

			ArgumentCaptor<ItemStack> slot = ArgumentCaptor.forClass(ItemStack.class);
			verify(inventory).setItemInOffHand(slot.capture());
			ArgumentCaptor<ItemStack> added = ArgumentCaptor.forClass(ItemStack.class);
			verify(inventory, atLeast(0)).addItem(added.capture());

			assertEquals(2 * 10 + FUEL_PER_ITEM, totalFuel(slot.getValue(), added),
			             "one coal adds one coal's fuel across the whole stack");
		}
	}

}
