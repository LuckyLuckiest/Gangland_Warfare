package org.luckyraven.gangland.npcshops.trader.view;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.npcshops.trader.TraderData;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;
import org.luckyraven.gangland.npcshops.trader.config.TraderSettings;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;
import org.luckyraven.gangland.npcshops.trader.trait.TraderTraitDefinition;
import org.luckyraven.gangland.npcshops.trader.trait.TraderTraitProfile;
import org.luckyraven.keystone.cooldown.CooldownService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.shop.EntryKind;
import org.luckyraven.keystone.shop.ShopDefinition;
import org.luckyraven.keystone.shop.ShopItemEntry;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.valuation.ItemValuation;
import org.luckyraven.keystone.shop.valuation.SellValuator;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterises the dropzone helpers of {@link SellView} (geometry and cap, slot membership, shift-click placement,
 * item return) through its public bridges, before they are extracted next to {@code BarterView}'s identical copies.
 */
@DisplayName("SellView dropzone helpers")
class SellViewDropzoneTest {

	private static final Set<Integer> DROPZONE = Set.of(10, 11, 12, 13, 14, 19, 20, 21, 22, 23, 28, 29, 30, 31, 32, 37,
	                                                    38, 39, 40, 41);

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
		Server server = Bukkit.getServer();
		if (server != null) {
			when(server.getVersion()).thenReturn("git-Paper-1 (MC: 1.21.1)");
		}
		XMaterial.STONE.get();
	}

	private static Inventory fakeInventory(InventoryHolder holder, int size) {
		ItemStack[] contents = new ItemStack[size];
		Inventory   inv      = mock(Inventory.class);
		when(inv.getHolder()).thenReturn(holder);
		when(inv.getSize()).thenReturn(size);
		when(inv.getItem(anyInt())).thenAnswer(invocation -> {
			int idx = invocation.getArgument(0);
			return idx >= 0 && idx < contents.length ? contents[idx] : null;
		});
		doAnswer(invocation -> {
			int       idx  = invocation.getArgument(0);
			ItemStack item = invocation.getArgument(1);
			if (idx >= 0 && idx < contents.length) contents[idx] = item;
			return null;
		}).when(inv).setItem(anyInt(), any());
		return inv;
	}

	private record Rig(SellView view, Player player, PlayerInventory playerInventory, World world,
	                   MenuFlow<TraderFlowSession> flow, ChestMenu menu, Inventory top) { }

	private Rig buildRig(BukkitStatics bukkit, int offerSlotCap) {
		ItemFactory itemFactory = mock(ItemFactory.class);
		// the real factory treats two metas of one material as equal; the mock must too for ItemStack.isSimilar to hold
		when(itemFactory.equals(org.mockito.ArgumentMatchers.<ItemMeta>any(), org.mockito.ArgumentMatchers.<ItemMeta>any())).thenReturn(true);
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(itemFactory);
		bukkit.statics().when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
		      .thenAnswer(invocation -> fakeInventory(invocation.getArgument(0), invocation.getArgument(1)));

		MoodService moodService = mock(MoodService.class);
		when(moodService.priceMultiplier(any(), any(), any())).thenReturn(1.0);

		SellValuator valuator = mock(SellValuator.class);
		when(valuator.value(any(), any(), anyDouble(), anyDouble())).thenReturn(ItemValuation.UNKNOWN);
		ItemRefresherRegistry refresherRegistry = mock(ItemRefresherRegistry.class);
		when(refresherRegistry.decorate(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

		TraderSettings settings = mock(TraderSettings.class);
		when(settings.getSellMaxOfferSlots()).thenReturn(offerSlotCap);
		when(settings.getInventoryFillItem()).thenReturn("BLACK_STAINED_GLASS_PANE");
		when(settings.getInventoryFillName()).thenReturn(" ");

		ShopDisplayResolver displayResolver = mock(ShopDisplayResolver.class);
		when(displayResolver.cleanDisplayName(any())).thenReturn("Diamond");

		SellView view = new SellView(mock(JavaPlugin.class), moodService, valuator, refresherRegistry, settings,
		                             displayResolver);

		TraderData traderData = new TraderData(UUID.randomUUID(), "shop", null, "Trader", "trait");
		TraderNpc  trader     = mock(TraderNpc.class);
		when(trader.getData()).thenReturn(traderData);

		TraderTraitProfile    profile = new TraderTraitProfile(0, 0, 0, false, 1.0, 1.0, 20, false);
		TraderTraitDefinition trait   = new TraderTraitDefinition("trait", "Trait", profile);

		TraderFlowSession session = new TraderFlowSession(trader, ShopDefinition.empty("shop", "Shop", 54), trait);
		session.selectedEntry  = new ShopItemEntry(0, EntryKind.SELL, new ItemStack(Material.DIAMOND), BigDecimal.TEN);
		session.basePrice      = BigDecimal.TEN;
		session.moodMultiplier = 1.0;

		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		PlayerInventory playerInventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(playerInventory);
		when(playerInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		World world = mock(World.class);
		when(player.getWorld()).thenReturn(world);
		when(player.getOpenInventory()).thenReturn(mock(InventoryView.class));

		@SuppressWarnings("unchecked")
		MenuFlow<TraderFlowSession> flow = mock(MenuFlow.class);
		when(flow.viewer()).thenReturn(player);

		InventoryService inventoryService = new InventoryService(mock(CooldownService.class));
		ChestMenuBuilder builder          = ChestMenu.builder(inventoryService).title("&8Sell to Trait").rows(6);
		view.render(flow, builder, session);
		ChestMenu menu = builder.build();
		when(flow.currentMenu()).thenReturn(menu);
		menu.open(player);

		return new Rig(view, player, playerInventory, world, flow, menu, menu.bukkitInventory());
	}

	private static SellView.ClickOutcome clickTop(Rig rig, int slot) {
		return rig.view().handleClick(rig.player(), rig.top(), rig.top(), slot, InventoryAction.PICKUP_ALL, null);
	}

	private static SellView.ClickOutcome shiftClickFromPlayer(Rig rig, ItemStack stack) {
		return rig.view().handleClick(rig.player(), rig.top(), rig.playerInventory(), 0,
		                              InventoryAction.MOVE_TO_OTHER_INVENTORY, stack);
	}

	private static Set<Integer> allowedTopSlots(Rig rig) {
		Set<Integer> allowed = new TreeSet<>();
		for (int slot = 0; slot < 54; slot++) {
			SellView.ClickOutcome outcome = clickTop(rig, slot);
			if (outcome == SellView.ClickOutcome.ALLOW) allowed.add(slot);
			else assertSame(SellView.ClickOutcome.PASS, outcome);
		}
		return allowed;
	}

	@Test
	@DisplayName("a click on the top inventory is allowed on exactly the 20 dropzone slots and passed everywhere else")
	void topClick_allowedOnlyOnDropzoneSlots() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 20);

			assertEquals(new TreeSet<>(DROPZONE), allowedTopSlots(rig));
		}
	}

	@Test
	@DisplayName("the offer-slot cap keeps the first N dropzone slots, at least one and at most twenty")
	void offerSlotCap_limitsTheDropzone() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertEquals(Set.of(10, 11, 12), allowedTopSlots(buildRig(bukkit, 3)));
			assertEquals(Set.of(10), allowedTopSlots(buildRig(bukkit, 0)));
			assertEquals(Set.of(10), allowedTopSlots(buildRig(bukkit, -5)));
			assertEquals(new TreeSet<>(DROPZONE), allowedTopSlots(buildRig(bukkit, 100)));
			assertEquals(Set.of(10, 11, 12, 13, 14, 19, 20), allowedTopSlots(buildRig(bukkit, 7)));
		}
	}

	@Test
	@DisplayName("a click with no session, or on another inventory, is passed")
	void click_withoutSession_isPassed() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig    rig   = buildRig(bukkit, 20);
			Player other = mock(Player.class);

			assertSame(SellView.ClickOutcome.PASS,
			           rig.view().handleClick(other, rig.top(), rig.top(), 10, InventoryAction.PICKUP_ALL, null));
			assertSame(SellView.ClickOutcome.PASS,
			           rig.view().handleClick(rig.player(), mock(Inventory.class), rig.top(), 10,
			                                  InventoryAction.PICKUP_ALL, null));
		}
	}

	@Test
	@DisplayName("shift-click into an empty dropzone fills slot 10 and consumes the whole stack")
	void shiftClick_emptyDropzone_fillsFirstSlot() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig       rig   = buildRig(bukkit, 20);
			ItemStack stack = new ItemStack(Material.GOLD_INGOT, 5);

			SellView.ClickOutcome outcome = shiftClickFromPlayer(rig, stack);

			assertTrue(outcome.cancel());
			assertNull(outcome.replacementCurrent());
			assertTrue(outcome.replace());
			assertEquals(5, rig.top().getItem(10).getAmount());
			assertEquals(Material.GOLD_INGOT, rig.top().getItem(10).getType());
			assertNull(rig.top().getItem(11));
			assertEquals(5, stack.getAmount(), "the clicked stack itself is not mutated");
		}
	}

	@Test
	@DisplayName("shift-click of more than a stack spills into the next dropzone slot")
	void shiftClick_overflowsIntoNextSlot() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 20);

			SellView.ClickOutcome outcome = shiftClickFromPlayer(rig, new ItemStack(Material.GOLD_INGOT, 100));

			assertTrue(outcome.cancel());
			assertNull(outcome.replacementCurrent());
			assertEquals(64, rig.top().getItem(10).getAmount());
			assertEquals(36, rig.top().getItem(11).getAmount());
			assertNull(rig.top().getItem(12));
		}
	}

	@Test
	@DisplayName("shift-click tops up a similar partial stack first, then uses the next empty slot")
	void shiftClick_topsUpSimilarStack() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 20);
			rig.top().setItem(10, new ItemStack(Material.GOLD_INGOT, 60));

			SellView.ClickOutcome outcome = shiftClickFromPlayer(rig, new ItemStack(Material.GOLD_INGOT, 10));

			assertTrue(outcome.cancel());
			assertNull(outcome.replacementCurrent());
			assertEquals(64, rig.top().getItem(10).getAmount());
			assertEquals(6, rig.top().getItem(11).getAmount());
		}
	}

	@Test
	@DisplayName("shift-click skips a slot holding a different item and a full similar stack")
	void shiftClick_skipsOccupiedSlots() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 20);
			rig.top().setItem(10, new ItemStack(Material.STONE, 3));
			rig.top().setItem(11, new ItemStack(Material.GOLD_INGOT, 64));

			SellView.ClickOutcome outcome = shiftClickFromPlayer(rig, new ItemStack(Material.GOLD_INGOT, 2));

			assertTrue(outcome.cancel());
			assertNull(outcome.replacementCurrent());
			assertEquals(3, rig.top().getItem(10).getAmount());
			assertEquals(64, rig.top().getItem(11).getAmount());
			assertEquals(2, rig.top().getItem(12).getAmount());
		}
	}

	@Test
	@DisplayName("shift-click that only partly fits returns the remainder as the replacement stack")
	void shiftClick_partialFit_returnsRemainder() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 1);

			SellView.ClickOutcome outcome = shiftClickFromPlayer(rig, new ItemStack(Material.GOLD_INGOT, 100));

			assertTrue(outcome.cancel());
			assertEquals(36, outcome.replacementCurrent().getAmount());
			assertEquals(Material.GOLD_INGOT, outcome.replacementCurrent().getType());
			assertEquals(64, rig.top().getItem(10).getAmount());
			assertNotEquals(Material.GOLD_INGOT, rig.top().getItem(11).getType());
		}
	}

	@Test
	@DisplayName("shift-click with no room cancels the click and places nothing")
	void shiftClick_noRoom_cancelOnly() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 1);
			rig.top().setItem(10, new ItemStack(Material.GOLD_INGOT, 64));

			SellView.ClickOutcome outcome = shiftClickFromPlayer(rig, new ItemStack(Material.GOLD_INGOT, 5));

			assertSame(SellView.ClickOutcome.CANCEL_ONLY, outcome);
			assertEquals(64, rig.top().getItem(10).getAmount());
			assertNotEquals(Material.GOLD_INGOT, rig.top().getItem(11).getType());
		}
	}

	@Test
	@DisplayName("shift-click of nothing, or any other action from the player inventory, is passed")
	void shiftClick_nothingOrOtherAction_isPassed() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 20);

			assertSame(SellView.ClickOutcome.PASS, shiftClickFromPlayer(rig, null));
			assertSame(SellView.ClickOutcome.PASS, shiftClickFromPlayer(rig, new ItemStack(Material.AIR)));
			assertSame(SellView.ClickOutcome.PASS,
			           rig.view().handleClick(rig.player(), rig.top(), rig.playerInventory(), 0,
			                                  InventoryAction.PICKUP_ALL, new ItemStack(Material.GOLD_INGOT, 5)));
			assertNull(rig.top().getItem(10));
		}
	}

	@Test
	@DisplayName("a drag is cancelled when it touches a non-dropzone top slot, allowed on dropzone and bottom slots")
	void drag_cancelsOnlyOnNonDropzoneTopSlots() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 20);

			assertTrue(rig.view().handleDrag(rig.player(), rig.top(), List.of(0)));
			assertTrue(rig.view().handleDrag(rig.player(), rig.top(), List.of(10, 15)));
			assertTrue(rig.view().handleDrag(rig.player(), rig.top(), List.of(10, 0)));
			assertFalse(rig.view().handleDrag(rig.player(), rig.top(), List.of(10, 11, 41)));
			assertFalse(rig.view().handleDrag(rig.player(), rig.top(), List.of(10, 54, 70)));
			assertFalse(rig.view().handleDrag(rig.player(), rig.top(), List.of()));
			assertFalse(rig.view().handleDrag(mock(Player.class), rig.top(), List.of(0)));
		}
	}

	@Test
	@DisplayName("shutdown returns dropzone items to the player in slot order and clears only the dropzone")
	void shutdown_returnsDropzoneItemsInSlotOrder() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig       rig     = buildRig(bukkit, 20);
			ItemStack gold    = new ItemStack(Material.GOLD_INGOT, 5);
			ItemStack diamond = new ItemStack(Material.DIAMOND, 2);
			ItemStack stone   = new ItemStack(Material.STONE, 9);
			rig.top().setItem(19, diamond);
			rig.top().setItem(10, gold);
			rig.top().setItem(0, stone);
			rig.top().setItem(11, new ItemStack(Material.AIR));

			rig.view().onShutdown();

			InOrder order = inOrder(rig.playerInventory());
			ArgumentCaptor<ItemStack> returned = ArgumentCaptor.forClass(ItemStack.class);
			order.verify(rig.playerInventory(), times(2)).addItem(returned.capture());
			assertEquals(Material.GOLD_INGOT, returned.getAllValues().get(0).getType());
			assertEquals(5, returned.getAllValues().get(0).getAmount());
			assertEquals(Material.DIAMOND, returned.getAllValues().get(1).getType());
			assertEquals(2, returned.getAllValues().get(1).getAmount());
			assertNull(rig.top().getItem(10));
			assertNull(rig.top().getItem(19));
			assertSame(stone, rig.top().getItem(0));
			verify(rig.player()).closeInventory();
			assertSame(SellView.ClickOutcome.PASS, clickTop(rig, 10), "the session entry is gone after shutdown");
		}
	}

	@Test
	@DisplayName("items that do not fit the player inventory are dropped at the player")
	void shutdown_dropsLeftovers() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig       rig      = buildRig(bukkit, 20);
			ItemStack leftover = new ItemStack(Material.GOLD_INGOT, 3);
			when(rig.playerInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>(Map.of(0, leftover)));
			rig.top().setItem(10, new ItemStack(Material.GOLD_INGOT, 5));

			rig.view().onShutdown();

			verify(rig.world()).dropItemNaturally(any(), org.mockito.ArgumentMatchers.same(leftover));
			assertNull(rig.top().getItem(10));
		}
	}

	@Test
	@DisplayName("shutdown with no open menu returns nothing but still closes the viewer's inventory")
	void shutdown_withoutMenu_returnsNothing() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 20);
			rig.top().setItem(10, new ItemStack(Material.GOLD_INGOT, 5));
			when(rig.flow().currentMenu()).thenReturn(null);

			rig.view().onShutdown();

			verify(rig.playerInventory(), never()).addItem(any(ItemStack.class));
			verify(rig.player()).closeInventory();
			assertEquals(5, rig.top().getItem(10).getAmount());
		}
	}

	@Test
	@DisplayName("the Clear button hands the dropzone items back and re-renders")
	void clearButton_returnsItems() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit, 20);
			rig.top().setItem(20, new ItemStack(Material.GOLD_INGOT, 4));
			InventoryClickEvent click = mock(InventoryClickEvent.class);
			when(click.getRawSlot()).thenReturn(51); // SellView.SLOT_CLEAR
			when(click.getClick()).thenReturn(ClickType.LEFT);

			rig.menu().dispatchClick(click, rig.player());

			verify(rig.playerInventory()).addItem(any(ItemStack.class));
			assertNull(rig.top().getItem(20));
			verify(rig.flow()).rerender();
		}
	}
}
