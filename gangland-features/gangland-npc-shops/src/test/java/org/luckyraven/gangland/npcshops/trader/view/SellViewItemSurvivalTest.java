package org.luckyraven.gangland.npcshops.trader.view;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import org.luckyraven.gangland.shop.EntryKind;
import org.luckyraven.gangland.shop.ShopDefinition;
import org.luckyraven.gangland.shop.ShopItemEntry;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;
import org.luckyraven.gangland.shop.valuation.ItemValuation;
import org.luckyraven.gangland.shop.valuation.SellValuator;
import org.luckyraven.keystone.cooldown.CooldownService;
import org.luckyraven.keystone.inventory.CloseReason;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS2 G4 §0d item-survival regression guard for {@link SellView} — see {@link BarterViewItemSurvivalTest}'s class
 * doc for the full rationale (including fix round 1 F1's {@code MenuFlow.rerender()} body fidelity note); this
 * class exercises the identical {@link DropzoneSlotComponent} mechanism against {@code SellView}'s own panel
 * geometry/session wiring so a divergence in either view's {@code renderChrome} (e.g. forgetting to declare a
 * {@code DropzoneSlotComponent} for one of its dropzone slots) is caught independently.
 */
@DisplayName("SellView dropzone item survival (WS2 G4 §0d)")
class SellViewItemSurvivalTest {

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

	private record Rig(SellView view, TraderFlowSession session, InventoryService inventoryService,
	                   MenuFlow<TraderFlowSession> flow, ChestMenu menu, int dropzoneSlot, Player player,
	                   ItemStack dropped) { }

	private Rig buildRig(BukkitStatics bukkit) {
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(mock(ItemFactory.class));
		bukkit.statics().when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
		      .thenAnswer(invocation -> fakeInventory(invocation.getArgument(0), invocation.getArgument(1)));

		JavaPlugin plugin = mock(JavaPlugin.class);

		MoodService moodService = mock(MoodService.class);
		when(moodService.priceMultiplier(any(), any(), any())).thenReturn(1.0);

		SellValuator           valuator          = mock(SellValuator.class);
		// F1's rerender path renders a dropzone that now actually holds an item (unlike the first, empty render),
		// so recomputeOffer's valuation lookup runs for real — stub it to the "not accepted" constant rather than
		// Mockito's null default, which NPEs on ItemValuation.hasValue().
		when(valuator.value(any(), any(), anyDouble(), anyDouble())).thenReturn(ItemValuation.UNKNOWN);
		ItemRefresherRegistry  refresherRegistry = mock(ItemRefresherRegistry.class);
		when(refresherRegistry.decorate(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

		TraderSettings settings = mock(TraderSettings.class);
		when(settings.getSellMaxOfferSlots()).thenReturn(20);
		when(settings.getInventoryFillItem()).thenReturn("BLACK_STAINED_GLASS_PANE");
		when(settings.getInventoryFillName()).thenReturn(" ");

		ShopDisplayResolver displayResolver = mock(ShopDisplayResolver.class);
		when(displayResolver.cleanDisplayName(any())).thenReturn("Diamond");

		SellView view = new SellView(plugin, moodService, valuator, refresherRegistry, settings, displayResolver);

		TraderData traderData = new TraderData(UUID.randomUUID(), "shop", null, "Trader", "trait");
		TraderNpc  trader     = mock(TraderNpc.class);
		when(trader.getData()).thenReturn(traderData);

		ShopDefinition definition = ShopDefinition.empty("shop", "Shop", 54);

		TraderTraitProfile    profile = new TraderTraitProfile(0, 0, 0, false, 1.0, 1.0, 20, false);
		TraderTraitDefinition trait   = new TraderTraitDefinition("trait", "Trait", profile);

		TraderFlowSession session = new TraderFlowSession(trader, definition, trait);
		session.selectedEntry  = new ShopItemEntry(0, EntryKind.SELL, new ItemStack(Material.DIAMOND), BigDecimal.TEN);
		session.basePrice      = BigDecimal.TEN;
		session.moodMultiplier = 1.0;

		InventoryService inventoryService = new InventoryService(mock(CooldownService.class));

		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		PlayerInventory playerInventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(playerInventory);
		when(playerInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		when(player.getWorld()).thenReturn(mock(World.class));
		// No player.getOpenInventory() stub: ChestMenu.close(Player) reads it, but the read itself throws
		// IncompatibleClassChangeError against this mock regardless of what it's stubbed to return (a pre-existing
		// Keystone-vs-Gangland bukkit.version mismatch — see closeIgnoringKnownBukkitVersionGap below), so any stub
		// here is unreachable.

		@SuppressWarnings("unchecked")
		MenuFlow<TraderFlowSession> flow = mock(MenuFlow.class);
		when(flow.viewer()).thenReturn(player);

		ChestMenuBuilder builder = ChestMenu.builder(inventoryService).title("&8Sell to Trait").rows(6);
		view.render(flow, builder, session);
		ChestMenu menu = builder.build();
		when(flow.currentMenu()).thenReturn(menu);

		menu.open(player);

		int       dropzoneSlot = 10; // SellView.ALL_DROPZONE_SLOTS[0]
		ItemStack dropped      = new ItemStack(Material.GOLD_INGOT, 5);
		menu.bukkitInventory().setItem(dropzoneSlot, dropped);

		return new Rig(view, session, inventoryService, flow, menu, dropzoneSlot, player, dropped);
	}

	@Test
	@DisplayName("Escape / a genuine user close returns the dropped item")
	void userClose_returnsDroppedItem() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit);

			rig.menu().returnHeldItemsOnUserClose(rig.player());

			verify(rig.player().getInventory()).addItem(rig.dropped());
			assertNull(rig.menu().bukkitInventory().getItem(rig.dropzoneSlot()), "slot must be cleared after return");
		}
	}

	@Test
	@DisplayName("disconnect (PLAYER_DISCONNECT close) returns the dropped item")
	void disconnect_returnsDroppedItem() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit);

			closeIgnoringKnownBukkitVersionGap(rig, CloseReason.PLAYER_DISCONNECT);

			verify(rig.player().getInventory()).addItem(rig.dropped());
		}
	}

	@Test
	@DisplayName("a panel switch (NAVIGATION close) returns the dropped item")
	void panelSwitch_returnsDroppedItem() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit);

			// MenuFlow.switchInternal's full-rebuild branch closes the OUTGOING menu with CloseReason.NAVIGATION
			// before opening the next panel — exactly this call — whenever the target panel's size/title don't
			// match (true for sell -> mode-select, which differ in both).
			closeIgnoringKnownBukkitVersionGap(rig, CloseReason.NAVIGATION);

			verify(rig.player().getInventory()).addItem(rig.dropped());
		}
	}

	/** See {@link BarterViewItemSurvivalTest#closeIgnoringKnownBukkitVersionGap} — same pre-existing, cross-repo
	 *  Keystone (bukkit.version 1.16.5) vs. Gangland (1.21.11) {@code InventoryView} class/interface test-only gap;
	 *  the item-return this test checks already completed by the time this throws. */
	private void closeIgnoringKnownBukkitVersionGap(Rig rig, CloseReason reason) {
		try {
			rig.menu().close(rig.player(), reason);
		} catch (IncompatibleClassChangeError knownBukkitVersionGap) {
			// expected in this environment — see javadoc above.
		}
	}

	@Test
	@DisplayName("round-trip: the dropped item reads back through ChestMenu.bukkitInventory() and survives MenuFlow.rerender()'s real body")
	void roundTrip_survivesRerender() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit);

			assertEquals(rig.dropped(), rig.menu().bukkitInventory().getItem(rig.dropzoneSlot()));

			// Fix round 1, F1: the literal body of MenuFlow.rerender() — a FRESH ChestMenuBuilder, the same panel
			// re-rendered into it, then ChestMenu.adoptComponentsFrom(fresh.build()) on the LIVE menu. The plain
			// ChestMenu.rerender() this test previously called only repaints the already-live component set —
			// production never calls that method from MenuFlow at all. See BarterViewItemSurvivalTest's sibling
			// for the full rationale.
			ChestMenuBuilder freshBuilder = ChestMenu.builder(rig.inventoryService())
			                                         .title(rig.view().title(rig.session()))
			                                         .rows(rig.view().rows(rig.session()));
			rig.view().render(rig.flow(), freshBuilder, rig.session());
			rig.menu().adoptComponentsFrom(freshBuilder.build());

			assertEquals(rig.dropped(), rig.menu().bukkitInventory().getItem(rig.dropzoneSlot()),
			            "adoptComponentsFrom (MenuFlow.rerender()'s real body) must not disturb a physically-dropped item");
		}
	}

}
