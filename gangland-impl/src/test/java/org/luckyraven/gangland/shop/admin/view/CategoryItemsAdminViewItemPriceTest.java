package org.luckyraven.gangland.shop.admin.view;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.click.ClickHandler;
import org.luckyraven.keystone.inventory.component.Component;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.component.SlotView;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.item.nbt.ItemNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.shop.BarterCategory;
import org.luckyraven.keystone.shop.SellCategory;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.valuation.CategorySellValuator;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The per-item price editor of the SELL and BARTER category panels: the price it opens on is the item's own
 * {@code sell_price} tag when that parses, else the category's base price. Both panels carry the same block, so every
 * case runs against both.
 */
@DisplayName("Sell/Barter category item panels - the price a per-item editor opens on")
class CategoryItemsAdminViewItemPriceTest {

	private static final String     KEY  = CategorySellValuator.SELL_PRICE_NBT_KEY;
	private static final BigDecimal BASE = new BigDecimal("5.00");

	enum Panel {
		SELL,
		BARTER
	}

	private BukkitStatics     bukkit;
	private ItemNbtAccessor   nbt;
	private ItemStack         item;
	private Player            viewer;
	private MenuFlow<ShopAdminFlowSession> flow;

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

		nbt = mock(ItemNbtAccessor.class);
		NbtBridge.install(nbt);

		item = mock(ItemStack.class);
		when(item.clone()).thenReturn(item);

		viewer = mock(Player.class);
		flow   = mock(MenuFlow.class);
		when(flow.viewer()).thenReturn(viewer);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
		bukkit.close();
	}

	@ParameterizedTest
	@EnumSource(Panel.class)
	@DisplayName("an item with no price tag opens on the category's base price, and its tag is never read")
	void noTag_usesTheBasePrice(Panel panel) {
		when(nbt.has(item, KEY)).thenReturn(false);

		ShopAdminFlowSession session = openItemEditor(panel);

		assertEquals(BASE, session.priceEditOriginal);
		assertEquals(BASE, session.priceEditStaged);
		verify(nbt, never()).get(any(), any());
	}

	@ParameterizedTest
	@EnumSource(Panel.class)
	@DisplayName("a numeric tag opens on BigDecimal.valueOf of its double value")
	void numberTag_usesItsDoubleValue(Panel panel) {
		when(nbt.has(item, KEY)).thenReturn(true);
		when(nbt.get(item, KEY)).thenReturn(2.5D);

		ShopAdminFlowSession session = openItemEditor(panel);

		assertEquals(BigDecimal.valueOf(2.5D), session.priceEditOriginal);
		assertEquals(BigDecimal.valueOf(2.5D), session.priceEditStaged);
		verify(nbt, times(1)).get(item, KEY);
	}

	@ParameterizedTest
	@EnumSource(Panel.class)
	@DisplayName("an integer tag goes through its double value, so 7 opens on 7.0")
	void integerTag_goesThroughTheDoubleValue(Panel panel) {
		when(nbt.has(item, KEY)).thenReturn(true);
		when(nbt.get(item, KEY)).thenReturn(7);

		assertEquals(BigDecimal.valueOf(7.0D), openItemEditor(panel).priceEditOriginal);
	}

	@ParameterizedTest
	@EnumSource(Panel.class)
	@DisplayName("a string tag is trimmed and parsed as is, keeping its scale")
	void stringTag_isTrimmedAndParsed(Panel panel) {
		when(nbt.has(item, KEY)).thenReturn(true);
		when(nbt.get(item, KEY)).thenReturn("  3.50 ");

		ShopAdminFlowSession session = openItemEditor(panel);

		assertEquals(new BigDecimal("3.50"), session.priceEditOriginal);
		assertEquals(new BigDecimal("3.50"), session.priceEditStaged);
	}

	@ParameterizedTest
	@EnumSource(Panel.class)
	@DisplayName("a string tag that is not a number falls back to the base price")
	void unparsableString_usesTheBasePrice(Panel panel) {
		when(nbt.has(item, KEY)).thenReturn(true);
		when(nbt.get(item, KEY)).thenReturn("abc");

		assertEquals(BASE, openItemEditor(panel).priceEditOriginal);
	}

	@ParameterizedTest
	@EnumSource(Panel.class)
	@DisplayName("a tag of another type is read through String.valueOf and falls back when that is no number")
	void otherType_isReadThroughItsString(Panel panel) {
		when(nbt.has(item, KEY)).thenReturn(true);
		when(nbt.get(item, KEY)).thenReturn(Boolean.TRUE);

		assertEquals(BASE, openItemEditor(panel).priceEditOriginal);
	}

	@ParameterizedTest
	@EnumSource(Panel.class)
	@DisplayName("a present tag whose value is null falls back to the base price")
	void nullValue_usesTheBasePrice(Panel panel) {
		when(nbt.has(item, KEY)).thenReturn(true);
		when(nbt.get(item, KEY)).thenReturn(null);

		ShopAdminFlowSession session = openItemEditor(panel);

		assertEquals(BASE, session.priceEditOriginal);
		verify(nbt, times(1)).get(item, KEY);
	}

	@ParameterizedTest
	@EnumSource(Panel.class)
	@DisplayName("a NaN double is not caught: the click throws NumberFormatException and opens no editor")
	void nanTag_throws(Panel panel) {
		when(nbt.has(item, KEY)).thenReturn(true);
		when(nbt.get(item, KEY)).thenReturn(Double.NaN);

		org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class, () -> openItemEditor(panel));
		verify(flow, never()).switchTo(any());
	}

	@Test
	@DisplayName("the editor opens on the clicked item, in per-item mode, and the panel switches to the price editor")
	void editor_isOpenedOnTheClickedItem() {
		when(nbt.has(item, KEY)).thenReturn(false);

		ShopAdminFlowSession session = openItemEditor(Panel.SELL);

		assertSame(item, session.priceEditItem);
		assertEquals(1, session.priceEditMode);
		verify(flow).switchTo(ShopAdminFlowSession.PANEL_PRICE_EDITOR);
	}

	// ── helpers ──────────────────────────────────────────────────────────

	/** Renders the panel for a category holding {@link #item} and clicks (left) the item in its first slot. */
	private ShopAdminFlowSession openItemEditor(Panel panel) {
		ShopAdminFlowSession session = new ShopAdminFlowSession(null, mock(ItemRefresherRegistry.class), List.of(),
		                                                        new ArrayList<>(), new ArrayList<>());
		JavaPlugin           plugin  = mock(JavaPlugin.class);
		ShopDisplayResolver  display = mock(ShopDisplayResolver.class);
		ChestMenuBuilder     builder = mock(ChestMenuBuilder.class);

		if (panel == Panel.SELL) {
			session.sellCategoryInEdit = new SellCategory("ores", "Ores", BASE, new ArrayList<>(List.of(item)));
			new SellCategoryItemsAdminView(plugin, session.refresherRegistry, display).render(flow, builder, session);
		} else {
			session.barterCategoryInEdit = new BarterCategory("gems", "Gems", BASE, new ArrayList<>(List.of(item)));
			new BarterCategoryItemsAdminView(plugin, session.refresherRegistry, display).render(flow, builder, session);
		}

		leftClickOf(builder, 0).onClick(null);
		return session;
	}

	private static ClickHandler leftClickOf(ChestMenuBuilder builder, int slot) {
		ArgumentCaptor<Component<?>> component = ArgumentCaptor.forClass(Component.class);
		verify(builder, atLeastOnce()).slot(eq(slot), component.capture());

		SlotView                     slotView = mock(SlotView.class);
		ArgumentCaptor<ClickHandler> left     = ArgumentCaptor.forClass(ClickHandler.class);
		((ItemComponent) component.getValue()).renderInto(slotView, null);
		verify(slotView).onLeftClick(left.capture());
		return left.getValue();
	}
}
