package org.luckyraven.gangland.shop.admin.view;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.shop.BarterCategory;
import org.luckyraven.keystone.shop.EntryKind;
import org.luckyraven.keystone.shop.SellCategory;
import org.luckyraven.keystone.shop.ShopDefinition;
import org.luckyraven.keystone.shop.ShopItemEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Open + close with no edits must not resave the shop (it would drop entries the reader skipped at load). */
class ShopAdminFlowSessionTest {

	private final ShopDefinition original = new ShopDefinition(
			"shop", "Shop", 54,
			List.of(new ShopItemEntry(0, EntryKind.BUY, mock(ItemStack.class), BigDecimal.TEN)), List.of(),
			List.of(new SellCategory("ores", "Ores", BigDecimal.ONE, List.of(mock(ItemStack.class)))),
			List.of(new BarterCategory("gems", "Gems", BigDecimal.ONE, List.of(mock(ItemStack.class)))));

	/** Same working copies {@code ShopAdminFlow.start} hands the session. */
	private ShopAdminFlowSession open() {
		List<SellCategory> sell = new ArrayList<>();
		for (SellCategory c : original.getSellCategories())
			sell.add(new SellCategory(c.getId(), c.getDisplayName(), c.getBasePrice(), c.getItems()));
		List<BarterCategory> barter = new ArrayList<>();
		for (BarterCategory c : original.getBarterCategories())
			barter.add(new BarterCategory(c.getId(), c.getDisplayName(), c.getBasePrice(), c.getItems()));
		return new ShopAdminFlowSession(original, mock(ItemRefresherRegistry.class),
		                                new ArrayList<>(original.getBuyEntries()), sell, barter);
	}

	@Test
	void untouchedSession_isNotDirty() {
		assertFalse(open().isDirty());
	}

	@Test
	void everyKindOfEdit_isDirty() {
		List<Consumer<ShopAdminFlowSession>> edits = List.of(
				s -> s.buyEntries.add(new ShopItemEntry(1, EntryKind.BUY, mock(ItemStack.class), BigDecimal.ONE)),
				s -> s.buyEntries.remove(0),
				s -> s.buyEntries.set(0, new ShopItemEntry(0, EntryKind.BUY, s.buyEntries.get(0).getItem(),
				                                           BigDecimal.ONE)),
				s -> s.sellCategories.add(SellCategory.empty("new")),
				s -> s.sellCategories.remove(0),
				s -> s.sellCategories.get(0).getItems().add(mock(ItemStack.class)),
				s -> s.sellCategories.get(0).getItems().remove(0),
				s -> s.sellCategories.get(0).getItems().set(0, mock(ItemStack.class)),
				s -> s.sellCategories.get(0).setBasePrice(BigDecimal.TEN),
				s -> s.barterCategories.add(BarterCategory.empty("new")),
				s -> s.barterCategories.remove(0),
				s -> s.barterCategories.get(0).getItems().add(mock(ItemStack.class)),
				s -> s.barterCategories.get(0).setBasePrice(BigDecimal.TEN));
		for (int i = 0; i < edits.size(); i++) {
			ShopAdminFlowSession session = open();
			edits.get(i).accept(session);
			assertTrue(session.isDirty(), "edit #" + i + " must mark the session dirty");
		}
	}

}
