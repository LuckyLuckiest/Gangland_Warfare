package org.luckyraven.gangland.item.listener.fuel;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.fuel.Fuel;
import org.luckyraven.gangland.item.fuel.FuelContract;
import org.luckyraven.gangland.item.fuel.FuelKey;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Red-first pin for review finding B3 (gangland-0.9.0.md T-R3): {@link FuelRefuelListener#onInventoryClick} must not
 * treat every fuel-item-to-fuel-item click as a transfer. Before the fix, dragging one gasoline can onto another
 * drained one into the other and cancelled the click; after the fix, only a container (not a
 * {@link FuelContract#isFuelSink}) onto a sink (a jetpack, per a stub contract) transfers.
 *
 * <p>Confirmed genuinely red pre-fix: a trimmed variant of {@link #containerToContainer_doesNotTransfer} run against
 * the pre-fix listener (unconditional {@code Fuel.isFuelItem(cursor) && Fuel.isFuelItem(clicked)}) entered
 * {@code tryTransferFuelToWearable} for a can-to-can click — proving the branch fired when it must not have.
 */
@DisplayName("FuelRefuelListener.onInventoryClick — container-to-sink guard (T-R3)")
class FuelRefuelListenerTest {

	private static final String FUEL_KEY = "gasoline";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Fuel.isFuelItem calls ItemStack.getType().isAir(), which on the 1.21 API reaches Registry.BLOCK.
		BukkitRegistryFixture.install();
	}

	private FuelContract       fuelContract;
	private FuelRefuelListener listener;
	private Player             player;
	private InventoryView      view;

	@BeforeEach
	void setUp() {
		NbtBridge.install(new PerStackNbtAccessor());

		fuelContract = mock(FuelContract.class); // isFuelSink defaults to false unless stubbed (interface default)
		listener     = new FuelRefuelListener(fuelContract);
		player       = mock(Player.class);
		view         = mock(InventoryView.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private ItemStack fuelItem(int current, int max) {
		ItemStack   stack   = new ItemStack(Material.STICK);
		ItemBuilder builder = new ItemBuilder(stack);
		builder.addTag(FuelKey.FUEL_ID.getKey(), FUEL_KEY);
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

	@Test
	@DisplayName("two plain fuel containers no longer transfer into each other")
	void containerToContainer_doesNotTransfer() {
		ItemStack containerA = fuelItem(50, 100);
		ItemStack containerB = fuelItem(30, 100);

		InventoryClickEvent event = clickEvent(containerA, containerB);

		listener.onInventoryClick(event);

		verify(event, never()).setCancelled(true);
		verify(view, never()).setCursor(any());
		verify(event, never()).setCurrentItem(any());
	}

	@Test
	@DisplayName("a container still transfers into a stub-reported sink")
	void containerToSink_stillTransfers() {
		ItemStack container = fuelItem(50, 100);
		ItemStack wearable  = fuelItem(10, 50);
		// same(), not equals-based matching: plain ItemStacks with no real ItemMeta compare equal to each other
		// (our fuel NBT lives in a fake per-stack accessor, invisible to ItemStack.equals), so an equals-matcher stub
		// would also match `container` and defeat the point of this test.
		when(fuelContract.isFuelSink(same(wearable))).thenReturn(true);

		InventoryClickEvent event = clickEvent(container, wearable);

		// ActionBarManager.send ends up in XSeries' NMS reflection (com.cryptomorin.xseries.messages.ActionBar),
		// which has no server to reflect against outside a real Bukkit runtime; stub it inert for this transfer path.
		try (MockedStatic<ActionBarManager> ignored = mockStatic(ActionBarManager.class)) {
			listener.onInventoryClick(event);
		}

		verify(event).setCancelled(true);

		var currentCaptor = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
		verify(event).setCurrentItem(currentCaptor.capture());
		assertEquals(50, Fuel.readFuelCurrent(currentCaptor.getValue()),
		             "the sink must receive the transferred fuel");

		var cursorCaptor = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
		verify(view).setCursor(cursorCaptor.capture());
		assertEquals(10, Fuel.readFuelCurrent(cursorCaptor.getValue()),
		             "the container must be drained by exactly what the sink accepted");
	}

}
