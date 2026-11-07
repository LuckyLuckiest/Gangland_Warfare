package org.luckyraven.gangland.shop.admin.view;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.shop.config.ShopUiSettings;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.click.ClickHandler;
import org.luckyraven.keystone.inventory.component.Component;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.component.SlotView;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.shop.BarterCategory;
import org.luckyraven.keystone.shop.EntryKind;
import org.luckyraven.keystone.shop.SellCategory;
import org.luckyraven.keystone.shop.ShopItemEntry;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.message.ShopMessageContract;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.NumberUtil;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The page loop of {@link ShopAdminView#render}: which interior slot shows which entry or category of the current
 * page, what the item says, and which index the left and right click of each slot act on. Pinned slot by slot so the
 * three lists (BUY entries, SELL categories, BARTER categories) can share one loop without a slot, an index or a
 * click binding drifting.
 */
@DisplayName("ShopAdminView - the paged BUY, SELL and BARTER lists")
class ShopAdminViewRenderTest {

	/** The 28 interior slots, page-local index 0 first. */
	private static final int[] INTERIOR = {
			10, 11, 12, 13, 14, 15, 16,
			19, 20, 21, 22, 23, 24, 25,
			28, 29, 30, 31, 32, 33, 34,
			37, 38, 39, 40, 41, 42, 43
	};

	private BukkitStatics         bukkit;
	private ShopMessageContract   messages;
	private ShopDisplayResolver   displayResolver;
	private ShopAdminView         view;
	private MenuFlow<ShopAdminFlowSession> flow;
	private Player                viewer;
	private ChestMenuBuilder      builder;

	private final Map<ItemStack, ItemMeta> metas  = new IdentityHashMap<>();
	private final Map<ItemStack, String>   labels = new IdentityHashMap<>();

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(mock(ItemFactory.class));
		// XMaterial$Data's static initialiser parses the NMS version out of the server's version string; a null one
		// would fail it for the rest of the fork.
		when(bukkit.server().getVersion()).thenReturn("git-Paper-1 (MC: 1.21.1)");
		bukkit.statics().when(Bukkit::getVersion).thenReturn("git-Paper-1 (MC: 1.21.1)");
		bukkit.statics().when(Bukkit::getBukkitVersion).thenReturn("1.21.1-R0.1-SNAPSHOT");

		messages        = mock(ShopMessageContract.class);
		displayResolver = mock(ShopDisplayResolver.class);
		when(displayResolver.cleanDisplayName(any())).thenAnswer(invocation -> labels.get(invocation.<ItemStack>getArgument(0)));
		when(messages.shopAdminEntryRemoved(anyInt())).thenAnswer(invocation -> "entry-removed-" + invocation.getArgument(0));
		when(messages.shopAdminCategoryRemoved(any())).thenAnswer(invocation -> "category-removed-" + invocation.getArgument(0));

		ShopUiSettings uiSettings = mock(ShopUiSettings.class);
		when(uiSettings.getInventoryFillItem()).thenReturn("BLACK_STAINED_GLASS_PANE");
		when(uiSettings.getInventoryFillName()).thenReturn(" ");

		view = new ShopAdminView(mock(JavaPlugin.class), mock(ItemRefresherRegistry.class), messages, uiSettings,
		                         displayResolver);

		viewer  = mock(Player.class);
		flow    = mock(MenuFlow.class);
		builder = mock(ChestMenuBuilder.class);
		when(flow.viewer()).thenReturn(viewer);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	// ── BUY ──────────────────────────────────────────────────────────────

	@Test
	@DisplayName("BUY: each entry of the first page fills its interior slot in order, with its index, price and lore")
	void buy_firstPage_fillsSlotsInOrder() {
		ShopAdminFlowSession session = session(entries(3), List.of(), List.of());

		view.render(flow, builder, session);

		Map<Integer, Rendered> list = interior(slots());
		assertEquals(List.of(10, 11, 12), List.copyOf(list.keySet()));
		for (int i = 0; i < 3; i++) {
			Rendered shown = list.get(INTERIOR[i]);
			ItemStack item = session.buyEntries.get(i).getItem();
			assertSame(item, shown.item, "the display is the entry's own stack");
			assertEquals(ChatUtil.color("label-" + i), nameOf(item));
			assertEquals(List.of(ChatUtil.color("&7Index: &f" + i),
			                     ChatUtil.color("&7Price: &6$" + NumberUtil.valueFormat(BigDecimal.valueOf(10 + i))),
			                     ChatUtil.color("&aL-click &7set price  &cR-click &7remove")), loreOf(item));
		}
	}

	@Test
	@DisplayName("BUY: an entry with no price shows no price line")
	void buy_entryWithoutPrice_hasNoPriceLine() {
		ShopAdminFlowSession session = session(List.of(new ShopItemEntry(0, EntryKind.BUY, stack("label-0"), null)),
		                                       List.of(), List.of());

		view.render(flow, builder, session);

		ItemStack item = session.buyEntries.get(0).getItem();
		assertEquals(List.of(ChatUtil.color("&7Index: &f0"),
		                     ChatUtil.color("&aL-click &7set price  &cR-click &7remove")), loreOf(item));
	}

	@Test
	@DisplayName("BUY: the second page starts at entry 28 in the first interior slot")
	void buy_secondPage_startsAtTheFirstSlot() {
		ShopAdminFlowSession session = session(entries(30), List.of(), List.of());
		session.currentPage = 1;

		view.render(flow, builder, session);

		Map<Integer, Rendered> list = interior(slots());
		assertEquals(List.of(10, 11), List.copyOf(list.keySet()));
		assertEquals(ChatUtil.color("&7Index: &f28"), loreOf(session.buyEntries.get(28).getItem()).get(0));
		assertEquals(ChatUtil.color("&7Index: &f29"), loreOf(session.buyEntries.get(29).getItem()).get(0));

		list.get(11).left.onClick(null);
		assertEquals("Slot 29", session.priceEditTitleSuffix, "the left click of slot 11 acts on entry 29");
		assertSame(session.buyEntries.get(29).getItem(), session.priceEditItem);
		verify(flow).switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
	}

	@Test
	@DisplayName("BUY: the right click of a slot removes exactly its entry, re-renders and tells the admin")
	void buy_rightClick_removesItsEntry() {
		ShopAdminFlowSession session = session(entries(3), List.of(), List.of());
		ItemStack            second  = session.buyEntries.get(1).getItem();

		view.render(flow, builder, session);
		interior(slots()).get(11).right.onClick(null);

		assertEquals(2, session.buyEntries.size());
		assertTrue(session.buyEntries.stream().noneMatch(entry -> entry.getItem() == second));
		verify(flow).rerender();
		verify(viewer).sendMessage("entry-removed-1");
	}

	@Test
	@DisplayName("BUY: the left click of each slot opens the price editor on its own entry")
	void buy_leftClick_opensThePriceEditorOnItsEntry() {
		ShopAdminFlowSession session = session(entries(3), List.of(), List.of());

		view.render(flow, builder, session);
		Map<Integer, Rendered> list = interior(slots());

		list.get(12).left.onClick(null);
		assertEquals("Slot 2", session.priceEditTitleSuffix);
		assertSame(session.buyEntries.get(2).getItem(), session.priceEditItem);
		assertEquals(BigDecimal.valueOf(12), session.priceEditOriginal);

		list.get(10).left.onClick(null);
		assertEquals("Slot 0", session.priceEditTitleSuffix);
		assertSame(session.buyEntries.get(0).getItem(), session.priceEditItem);
	}

	// ── SELL ─────────────────────────────────────────────────────────────

	@Test
	@DisplayName("SELL: each category of the page fills its slot with its icon, id, base price and item count")
	void sell_firstPage_fillsSlotsInOrder() {
		ShopAdminFlowSession session = session(List.of(), sellCategories(3), List.of());
		session.currentKind = EntryKind.SELL;

		view.render(flow, builder, session);

		Map<Integer, Rendered> list = interior(slots());
		assertEquals(List.of(10, 11, 12), List.copyOf(list.keySet()));
		for (int i = 0; i < 3; i++) {
			SellCategory category = session.sellCategories.get(i);
			ItemStack    icon     = category.getItems().get(0);
			assertSame(icon, list.get(INTERIOR[i]).item, "the icon is the category's first item");
			assertEquals(ChatUtil.color("&6Sell " + i), nameOf(icon));
			assertEquals(List.of(ChatUtil.color("&7ID: &f" + "sell" + i),
			                     ChatUtil.color("&7Base price: &6$" + NumberUtil.valueFormat(BigDecimal.valueOf(5 + i))),
			                     ChatUtil.color("&7Items: &f1"),
			                     ChatUtil.color("&aL-click &7edit items + price  &cR-click &7remove")), loreOf(icon));
		}
	}

	@Test
	@DisplayName("SELL: the second page starts at category 28 and its left click edits that category")
	void sell_secondPage_leftClickEditsItsCategory() {
		ShopAdminFlowSession session = session(List.of(), sellCategories(30), List.of());
		session.currentKind = EntryKind.SELL;
		session.currentPage = 1;

		view.render(flow, builder, session);
		Map<Integer, Rendered> list = interior(slots());

		assertEquals(List.of(10, 11), List.copyOf(list.keySet()));
		list.get(11).left.onClick(null);
		assertSame(session.sellCategories.get(29), session.sellCategoryInEdit);
		verify(flow).switchTo(ShopAdminFlowSession.PANEL_SELL_CATEGORY);
	}

	@Test
	@DisplayName("SELL: the right click of a slot removes exactly its category, re-renders and tells the admin")
	void sell_rightClick_removesItsCategory() {
		ShopAdminFlowSession session = session(List.of(), sellCategories(3), List.of());
		session.currentKind = EntryKind.SELL;
		SellCategory second = session.sellCategories.get(1);

		view.render(flow, builder, session);
		interior(slots()).get(11).right.onClick(null);

		assertEquals(2, session.sellCategories.size());
		assertTrue(session.sellCategories.stream().noneMatch(category -> category == second));
		verify(flow).rerender();
		verify(viewer).sendMessage("category-removed-sell1");
		assertNull(session.sellCategoryInEdit);
	}

	// ── BARTER ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("BARTER: each category of the page fills its slot with its icon, id, base value and item count")
	void barter_firstPage_fillsSlotsInOrder() {
		ShopAdminFlowSession session = session(List.of(), List.of(), barterCategories(3));
		session.currentKind = EntryKind.BARTER;

		view.render(flow, builder, session);

		Map<Integer, Rendered> list = interior(slots());
		assertEquals(List.of(10, 11, 12), List.copyOf(list.keySet()));
		for (int i = 0; i < 3; i++) {
			BarterCategory category = session.barterCategories.get(i);
			ItemStack      icon     = category.getItems().get(0);
			assertSame(icon, list.get(INTERIOR[i]).item, "the icon is the category's first item");
			assertEquals(ChatUtil.color("&bBarter " + i), nameOf(icon));
			assertEquals(List.of(ChatUtil.color("&7ID: &f" + "barter" + i),
			                     ChatUtil.color("&7Base value: &6$" + NumberUtil.valueFormat(BigDecimal.valueOf(7 + i))),
			                     ChatUtil.color("&7Items: &f1"),
			                     ChatUtil.color("&aL-click &7edit items + value  &cR-click &7remove")), loreOf(icon));
		}
	}

	@Test
	@DisplayName("BARTER: the second page starts at category 28 and its left click edits that category")
	void barter_secondPage_leftClickEditsItsCategory() {
		ShopAdminFlowSession session = session(List.of(), List.of(), barterCategories(30));
		session.currentKind = EntryKind.BARTER;
		session.currentPage = 1;

		view.render(flow, builder, session);
		Map<Integer, Rendered> list = interior(slots());

		assertEquals(List.of(10, 11), List.copyOf(list.keySet()));
		list.get(11).left.onClick(null);
		assertSame(session.barterCategories.get(29), session.barterCategoryInEdit);
		verify(flow).switchTo(ShopAdminFlowSession.PANEL_BARTER_CATEGORY);
	}

	@Test
	@DisplayName("BARTER: the right click of a slot removes exactly its category, re-renders and tells the admin")
	void barter_rightClick_removesItsCategory() {
		ShopAdminFlowSession session = session(List.of(), List.of(), barterCategories(3));
		session.currentKind = EntryKind.BARTER;
		BarterCategory second = session.barterCategories.get(1);

		view.render(flow, builder, session);
		interior(slots()).get(11).right.onClick(null);

		assertEquals(2, session.barterCategories.size());
		assertTrue(session.barterCategories.stream().noneMatch(category -> category == second));
		verify(flow).rerender();
		verify(viewer).sendMessage("category-removed-barter1");
		verify(flow, never()).switchTo(any());
	}

	@Test
	@DisplayName("an empty list fills no interior slot")
	void emptyList_fillsNoInteriorSlot() {
		view.render(flow, builder, session(List.of(), List.of(), List.of()));

		assertTrue(interior(slots()).isEmpty());
	}

	// ── helpers ──────────────────────────────────────────────────────────

	private record Rendered(ItemStack item, ClickHandler left, ClickHandler right) { }

	private ItemStack stack(String label) {
		ItemStack stack = mock(ItemStack.class);
		ItemMeta  meta  = mock(ItemMeta.class);
		when(stack.clone()).thenReturn(stack);
		when(stack.getItemMeta()).thenReturn(meta);
		metas.put(stack, meta);
		labels.put(stack, label);
		return stack;
	}

	private List<ShopItemEntry> entries(int count) {
		List<ShopItemEntry> entries = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			entries.add(new ShopItemEntry(i, EntryKind.BUY, stack("label-" + i), BigDecimal.valueOf(10 + i)));
		}
		return entries;
	}

	private List<SellCategory> sellCategories(int count) {
		List<SellCategory> categories = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			List<ItemStack> items = new ArrayList<>(List.of(stack("sell-icon-" + i)));
			categories.add(new SellCategory("sell" + i, "Sell " + i, BigDecimal.valueOf(5 + i), items));
		}
		return categories;
	}

	private List<BarterCategory> barterCategories(int count) {
		List<BarterCategory> categories = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			List<ItemStack> items = new ArrayList<>(List.of(stack("barter-icon-" + i)));
			categories.add(new BarterCategory("barter" + i, "Barter " + i, BigDecimal.valueOf(7 + i), items));
		}
		return categories;
	}

	private ShopAdminFlowSession session(List<ShopItemEntry> buy, List<SellCategory> sell,
	                                     List<BarterCategory> barter) {
		return new ShopAdminFlowSession(null, mock(ItemRefresherRegistry.class), buy, sell, barter);
	}

	/** Every {@code builder.slot(n, component)} the render made, last write of a slot wins. */
	private Map<Integer, Component<?>> slots() {
		ArgumentCaptor<Integer>      slot      = ArgumentCaptor.forClass(Integer.class);
		ArgumentCaptor<Component<?>> component = ArgumentCaptor.forClass(Component.class);
		verify(builder, org.mockito.Mockito.atLeast(0)).slot(slot.capture(), component.capture());

		Map<Integer, Component<?>> bySlot = new TreeMap<>();
		for (int i = 0; i < slot.getAllValues().size(); i++) {
			bySlot.put(slot.getAllValues().get(i), component.getAllValues().get(i));
		}
		return bySlot;
	}

	/** The entries of {@code slots} that sit in the interior grid, rendered into their item and click handlers. */
	private Map<Integer, Rendered> interior(Map<Integer, Component<?>> slots) {
		Map<Integer, Rendered> rendered = new TreeMap<>();
		for (int slot : INTERIOR) {
			Component<?> component = slots.get(slot);
			if (!(component instanceof ItemComponent item)) continue;

			SlotView                 slotView = mock(SlotView.class);
			ArgumentCaptor<ItemStack> shown    = ArgumentCaptor.forClass(ItemStack.class);
			ArgumentCaptor<ClickHandler> left  = ArgumentCaptor.forClass(ClickHandler.class);
			ArgumentCaptor<ClickHandler> right = ArgumentCaptor.forClass(ClickHandler.class);

			item.renderInto(slotView, null);

			verify(slotView).setItem(shown.capture());
			verify(slotView).onLeftClick(left.capture());
			verify(slotView).onRightClick(right.capture());
			rendered.put(slot, new Rendered(shown.getValue(), left.getValue(), right.getValue()));
		}
		return rendered;
	}

	private String nameOf(ItemStack stack) {
		ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
		verify(metas.get(stack)).setDisplayName(name.capture());
		return name.getValue();
	}

	@SuppressWarnings("unchecked")
	private List<String> loreOf(ItemStack stack) {
		ArgumentCaptor<List<String>> lore = ArgumentCaptor.forClass(List.class);
		verify(metas.get(stack)).setLore(lore.capture());
		return lore.getValue();
	}
}
