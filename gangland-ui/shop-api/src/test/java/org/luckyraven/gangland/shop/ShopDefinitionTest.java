package org.luckyraven.gangland.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the small pure-logic surface of {@link ShopDefinition}: {@link ShopDefinition#entriesOf} routes by
 * {@link EntryKind}, category lookup/removal is case-insensitive, {@link ShopDefinition#withTitle} produces a copy
 * that shares the entry/category lists (not a deep clone) rather than mutating the original, and a {@code null}
 * category list in the 4-arg constructor becomes an empty, mutable list rather than staying {@code null}.
 */
@DisplayName("ShopDefinition — entry/category lookup and immutable-style copies")
class ShopDefinitionTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	private ShopItemEntry buyEntry;
	private ShopItemEntry sellEntry;
	private SellCategory  weapons;

	@BeforeEach
	void setUp() {
		buyEntry  = new ShopItemEntry(0, EntryKind.BUY, new ItemStack(Material.IRON_SWORD), BigDecimal.TEN);
		sellEntry = new ShopItemEntry(1, EntryKind.SELL, new ItemStack(Material.DIAMOND), BigDecimal.ONE);
		weapons   = SellCategory.empty("Weapons");
	}

	@Test
	@DisplayName("entriesOf(BUY) and entriesOf(SELL) route to the correct backing list")
	void entriesOf_routesByKind() {
		ShopDefinition definition = new ShopDefinition("shop", "Shop", 54, List.of(buyEntry), List.of(sellEntry));

		assertEquals(List.of(buyEntry), definition.entriesOf(EntryKind.BUY));
		assertEquals(List.of(sellEntry), definition.entriesOf(EntryKind.SELL));
	}

	@Test
	@DisplayName("getSellCategoryById matches case-insensitively")
	void getSellCategoryById_caseInsensitive() {
		ShopDefinition definition = new ShopDefinition("shop", "Shop", 54, List.of(), List.of(), List.of(weapons));

		assertSame(weapons, definition.getSellCategoryById("weapons"));
		assertSame(weapons, definition.getSellCategoryById("WEAPONS"));
		assertNull(definition.getSellCategoryById("armor"));
		assertNull(definition.getSellCategoryById(null));
	}

	@Test
	@DisplayName("removeSellCategory matches case-insensitively and reports whether it removed anything")
	void removeSellCategory_caseInsensitive() {
		ShopDefinition definition = new ShopDefinition("shop", "Shop", 54, List.of(), List.of(),
		                                               new ArrayList<>(List.of(weapons)));

		assertTrue(definition.removeSellCategory("WEAPONS"));
		assertNull(definition.getSellCategoryById("Weapons"));
		assertFalse(definition.removeSellCategory("weapons"), "a second removal of the same id finds nothing left");
	}

	@Test
	@DisplayName("addBarterCategory / getBarterCategoryById / removeBarterCategory mirror the sell-side behaviour")
	void barterCategoryLookup_mirrorsSellSide() {
		ShopDefinition definition = ShopDefinition.empty("shop", "Shop", 54);
		BarterCategory barter     = BarterCategory.empty("Junk");

		definition.addBarterCategory(barter);

		assertSame(barter, definition.getBarterCategoryById("junk"));
		assertTrue(definition.removeBarterCategory("JUNK"));
		assertNull(definition.getBarterCategoryById("junk"));
	}

	@Test
	@DisplayName("withTitle returns a new instance sharing the same entry/category list references, not clones")
	void withTitle_copiesFieldsButSharesLists() {
		List<ShopItemEntry> buy = List.of(buyEntry);
		ShopDefinition original = new ShopDefinition("shop", "Old Title", 54, buy, List.of());

		ShopDefinition renamed = original.withTitle("New Title");

		assertEquals("New Title", renamed.getTitle());
		assertEquals("Old Title", original.getTitle(), "the original must be unaffected");
		assertSame(buy, renamed.getBuyEntries(), "withTitle is a shallow copy, not a deep clone");
		assertEquals(original.getKey(), renamed.getKey());
		assertEquals(original.getSize(), renamed.getSize());
	}

	@Test
	@DisplayName("a null sell-category / barter-category list in the constructor becomes an empty mutable list")
	void nullCategoryLists_becomeEmptyMutableLists() {
		ShopDefinition definition = new ShopDefinition("shop", "Shop", 54, List.of(), List.of(), null, null);

		assertTrue(definition.getSellCategories().isEmpty());
		assertTrue(definition.getBarterCategories().isEmpty());

		// must not throw UnsupportedOperationException — addSellCategory relies on a mutable backing list
		definition.addSellCategory(SellCategory.empty("new"));
		assertEquals(1, definition.getSellCategories().size());
	}

	@Test
	@DisplayName("ShopDefinition.empty produces a definition with every list empty")
	void empty_factory_producesEmptyLists() {
		ShopDefinition definition = ShopDefinition.empty("shop", "Shop", 27);

		assertTrue(definition.getBuyEntries().isEmpty());
		assertTrue(definition.getSellEntries().isEmpty());
		assertTrue(definition.getSellCategories().isEmpty());
		assertTrue(definition.getBarterCategories().isEmpty());
		assertEquals(27, definition.getSize());
	}

}
