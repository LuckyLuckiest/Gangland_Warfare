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
import org.luckyraven.keystone.shop.EntryKind;
import org.luckyraven.keystone.shop.ShopDefinition;
import org.luckyraven.keystone.shop.ShopItemEntry;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.valuation.CategoryBarterValuator;
import org.luckyraven.keystone.shop.valuation.ItemValuation;
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
 * WS2 G4 §0d item-survival regression guard. {@code BarterView}'s 20 dropzone slots are interactive
 * ({@code SlotView.interactive(true)}) but hold no built-in item-return guarantee on their own — per
 * {@code keystone-inventory.md}'s "item-return contract", only a component implementing
 * {@code ItemHoldingComponent} is guaranteed to get its held items back on close. {@link DropzoneSlotComponent}
 * is that component; this class pins that a real item a player drops into a real dropzone slot survives every
 * close path the contract promises, plus {@code MenuFlow.rerender()}'s real in-place-refresh body (re-render into
 * a fresh builder, then {@code ChestMenu.adoptComponentsFrom} — see {@link #roundTrip_survivesRerender()}, fix
 * round 1 F1: the plain {@code ChestMenu.rerender()} this test previously called only repaints the already-live
 * component set and is never what production actually calls).
 */
@DisplayName("BarterView dropzone item survival (WS2 G4 §0d)")
class BarterViewItemSurvivalTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
		Server server = Bukkit.getServer();
		if (server != null) {
			when(server.getVersion()).thenReturn("git-Paper-1 (MC: 1.21.1)");
		}
		// Force XMaterial's clinit now, against the real server above — see SimplePagedMenuTest's own note for why
		// (a BukkitStatics block below replaces Bukkit.getServer() with its own unstubbed mock).
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

	private record Rig(BarterView view, TraderFlowSession session, InventoryService inventoryService,
	                   MenuFlow<TraderFlowSession> flow, ChestMenu menu, int dropzoneSlot, Player player,
	                   ItemStack dropped) { }

	/** Builds a real {@code BarterView}, renders it into a real {@link ChestMenu}, opens it, and drops one item
	 *  directly into its first dropzone slot — exactly what an uncancelled Bukkit click on an interactive slot
	 *  would physically do (see {@code MenuListener}'s click policy). */
	private Rig buildRig(BukkitStatics bukkit) {
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(mock(ItemFactory.class));
		bukkit.statics().when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
		      .thenAnswer(invocation -> fakeInventory(invocation.getArgument(0), invocation.getArgument(1)));

		JavaPlugin plugin = mock(JavaPlugin.class);

		MoodService moodService = mock(MoodService.class);
		when(moodService.priceMultiplier(any(), any(), any())).thenReturn(1.0);

		CategoryBarterValuator valuator          = mock(CategoryBarterValuator.class);
		// F1's rerender path renders a dropzone that now actually holds an item (unlike the first, empty render),
		// so recomputeOffer's valuation lookup runs for real — stub it to the "not accepted" constant rather than
		// Mockito's null default, which NPEs on ItemValuation.hasValue().
		when(valuator.value(any(), any(), anyDouble(), anyDouble())).thenReturn(ItemValuation.UNKNOWN);
		ItemRefresherRegistry  refresherRegistry = mock(ItemRefresherRegistry.class);
		when(refresherRegistry.decorate(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

		ShopDisplayResolver displayResolver = mock(ShopDisplayResolver.class);
		when(displayResolver.cleanDisplayName(any())).thenReturn("Diamond");

		TraderSettings settings = mock(TraderSettings.class);
		when(settings.getSellMaxOfferSlots()).thenReturn(20);
		when(settings.getInventoryFillItem()).thenReturn("BLACK_STAINED_GLASS_PANE");
		when(settings.getInventoryFillName()).thenReturn(" ");

		BarterView view = new BarterView(plugin, moodService, valuator, refresherRegistry, displayResolver, settings);

		TraderData traderData = new TraderData(UUID.randomUUID(), "shop", null, "Trader", "trait");
		TraderNpc  trader     = mock(TraderNpc.class);
		when(trader.getData()).thenReturn(traderData);

		ShopDefinition definition = ShopDefinition.empty("shop", "Shop", 54);

		TraderTraitProfile    profile = new TraderTraitProfile(0, 0, 0, false, 1.0, 1.0, 20, false);
		TraderTraitDefinition trait   = new TraderTraitDefinition("trait", "Trait", profile);

		TraderFlowSession session = new TraderFlowSession(trader, definition, trait);
		session.selectedEntry  = new ShopItemEntry(0, EntryKind.BUY, new ItemStack(Material.DIAMOND), BigDecimal.TEN);
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

		ChestMenuBuilder builder = ChestMenu.builder(inventoryService).title("&8Barter for Diamond").rows(6);
		view.render(flow, builder, session);
		ChestMenu menu = builder.build();
		// Only stubbed after render(): render() itself never reads flow.currentMenu() (only viewer()) — matching
		// MenuFlow's own real ordering, where currentMenu is assigned only after the panel that's about to occupy
		// it has already rendered (see MenuFlow.switchInternal).
		when(flow.currentMenu()).thenReturn(menu);

		menu.open(player);

		int       dropzoneSlot = 10; // BarterView.ALL_DROPZONE_SLOTS[0]
		ItemStack dropped      = new ItemStack(Material.GOLD_INGOT, 3);
		menu.bukkitInventory().setItem(dropzoneSlot, dropped);

		return new Rig(view, session, inventoryService, flow, menu, dropzoneSlot, player, dropped);
	}

	@Test
	@DisplayName("Escape / a genuine user close returns the dropped item")
	void userClose_returnsDroppedItem() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Rig rig = buildRig(bukkit);

			// The exact hook MenuListener.onClose's tracked branch calls for a real Escape-press (not the
			// CloseReason-driven overload, which never fires for a genuine player close — see ChestMenu's doc).
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
			// match (true for barter -> negotiation, which differ in both).
			closeIgnoringKnownBukkitVersionGap(rig, CloseReason.NAVIGATION);

			verify(rig.player().getInventory()).addItem(rig.dropped());
		}
	}

	/**
	 * {@code ChestMenu.close(Player, CloseReason)} runs {@code returnHeldItems(...)} (the part this test verifies)
	 * FIRST, then unconditionally calls the 1-arg {@code close(Player)}, whose
	 * {@code player.getOpenInventory().getTopInventory()} bookkeeping check throws
	 * {@code IncompatibleClassChangeError} in this module's unit-test environment: {@code keystone-inventory} is
	 * compiled against Keystone's {@code bukkit.version} floor (1.16.5, where {@code InventoryView} is a class),
	 * while Gangland's own test classpath resolves the 1.21.11 spigot-api (where it is an interface) — a
	 * pre-existing, cross-repo API-version gap unrelated to this re-point (real CraftBukkit servers don't hit it;
	 * only Mockito's compile-time-shaped proxy does). The item-return this test cares about has already completed
	 * by the time this throws, so it's caught and ignored here rather than worked around by skipping the real
	 * {@code close(Player, CloseReason)} entry point entirely.
	 */
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
			// re-rendered into it, then ChestMenu.adoptComponentsFrom(fresh.build()) on the LIVE menu (components
			// cleared and replaced, interactiveSlots cleared and replaced from the builder's own floor — see
			// ChestMenu.java:378). The plain ChestMenu.rerender() this test previously called only repaints the
			// already-live component set — production never calls that method from MenuFlow at all.
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
