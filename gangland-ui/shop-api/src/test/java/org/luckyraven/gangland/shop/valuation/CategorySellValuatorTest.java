package org.luckyraven.gangland.shop.valuation;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.gangland.item.ItemSerializer;
import org.luckyraven.gangland.item.ItemSerializerRegistry;
import org.luckyraven.gangland.shop.SellCategory;
import org.luckyraven.gangland.shop.ShopDefinition;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Proves {@link CategorySellValuator#value}: template matching by {@link ItemSerializerRegistry} identity, the
 * per-item {@code sell_price} NBT tag overriding the category's {@code Base_Price}, division by the template's own
 * stack amount to get a per-item price, the {@code sellPriceRatio * moodMultiplier} multiplication with 2-dp
 * HALF_UP rounding, negative-result clamping to zero, and {@link ItemValuation#UNKNOWN} for an item that matches no
 * category.
 */
@DisplayName("CategorySellValuator — template matching and price arithmetic")
class CategorySellValuatorTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	private RecordingNbtAccessor  nbt;
	private ItemSerializerRegistry serializers;
	private CategorySellValuator   valuator;

	@BeforeEach
	void setUp() {
		nbt = new RecordingNbtAccessor();
		NbtBridge.install(nbt);

		serializers = new ItemSerializerRegistry();
		serializers.register(stack -> true, new ItemSerializer() {
			@Override
			public ItemKind kind() {
				return ItemKind.MATERIAL;
			}

			@Override
			public String extract(ItemStack stack) {
				return stack.getType().name();
			}
		});

		valuator = new CategorySellValuator(serializers);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private ShopDefinition definitionWithCategory(SellCategory category) {
		return new ShopDefinition("shop", "Shop", 54, List.of(), List.of(), List.of(category));
	}

	@Test
	@DisplayName("no matching category yields UNKNOWN")
	void value_noMatch_returnsUnknown() {
		SellCategory category = new SellCategory("gems", "Gems", new BigDecimal("40"),
		                                         List.of(new ItemStack(Material.DIAMOND, 1)));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.EMERALD, 1), 1.0, 1.0);

		assertSame(ItemValuation.UNKNOWN, result);
		assertFalse(result.hasValue());
	}

	@Test
	@DisplayName("without a sell_price NBT tag, per-item price is Base_Price divided by the template's own amount")
	void value_noNbtTag_usesBasePriceDividedByTemplateAmount() {
		ItemStack template = new ItemStack(Material.DIAMOND, 4); // a bundle of 4
		SellCategory category = new SellCategory("gems", "Gems", new BigDecimal("40"), List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.DIAMOND, 1), 1.0, 1.0);

		assertEquals(new BigDecimal("10.00"), result.unitPrice(), "40 / 4 template amount = 10 per item");
		assertEquals(ItemValuation.Source.CATEGORY, result.source());
		assertEquals("gems", result.categoryId());
	}

	@Test
	@DisplayName("a sell_price NBT tag on the template overrides Base_Price entirely")
	void value_withNbtTag_overridesBasePrice() {
		ItemStack template = new ItemStack(Material.DIAMOND, 4);
		new ItemBuilder(template).addTag(CategorySellValuator.SELL_PRICE_NBT_KEY, "20");
		SellCategory category = new SellCategory("gems", "Gems", new BigDecimal("40"), List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.DIAMOND, 1), 1.0, 1.0);

		assertEquals(new BigDecimal("5.00"), result.unitPrice(), "20 (NBT) / 4 template amount = 5 per item");
	}

	@Test
	@DisplayName("per-item division rounds HALF_UP at 2 dp (1 / 8 template = 0.125 -> 0.13, not 0.12)")
	void value_perItemDivision_roundsHalfUp() {
		ItemStack template = new ItemStack(Material.DIAMOND, 8); // a bundle of 8, base price 1 -> exactly 0.125/item
		SellCategory category = new SellCategory("gems", "Gems", BigDecimal.ONE, List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.DIAMOND, 1), 1.0, 1.0);

		assertEquals(new BigDecimal("0.13"), result.unitPrice(), "0.125 must round HALF_UP to 0.13, not truncate to 0.12");
	}

	@Test
	@DisplayName("the result scales by sellPriceRatio * moodMultiplier")
	void value_scalesByRatioAndMood() {
		ItemStack template = new ItemStack(Material.DIAMOND, 1);
		SellCategory category = new SellCategory("gems", "Gems", new BigDecimal("10"), List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		// perItem = 10.00; ratio 0.5 * mood 0.75 = 0.375 (exact in binary) -> 10.00 * 0.375 = 3.75
		ItemValuation result = valuator.value(definition, new ItemStack(Material.DIAMOND, 1), 0.5, 0.75);

		assertEquals(new BigDecimal("3.75"), result.unitPrice());
	}

	@Test
	@DisplayName("a negative computed price (e.g. a negative ratio) clamps to zero rather than going negative")
	void value_negativeRatio_clampsToZero() {
		ItemStack template = new ItemStack(Material.DIAMOND, 1);
		SellCategory category = new SellCategory("gems", "Gems", new BigDecimal("10"), List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.DIAMOND, 1), -1.0, 1.0);

		// Production clamps to a bare BigDecimal.ZERO (scale 0), while the non-clamped paths return scale 2,
		// so compare by value — BigDecimal.equals is scale-sensitive.
		assertEquals(0, result.unitPrice().compareTo(BigDecimal.ZERO), "a negative price clamps to zero");
	}

	@Test
	@DisplayName("a null definition or null stack returns UNKNOWN rather than throwing")
	void value_nullInputs_returnUnknown() {
		SellCategory   category   = SellCategory.empty("gems");
		ShopDefinition definition = definitionWithCategory(category);

		assertSame(ItemValuation.UNKNOWN, valuator.value(null, new ItemStack(Material.DIAMOND, 1), 1.0, 1.0));
		assertSame(ItemValuation.UNKNOWN, valuator.value(definition, null, 1.0, 1.0));
	}

	@Test
	@DisplayName("the first matching category wins when more than one category is present")
	void value_multipleCategories_firstMatchWins() {
		ItemStack firstTemplate  = new ItemStack(Material.DIAMOND, 1);
		ItemStack secondTemplate = new ItemStack(Material.DIAMOND, 1);
		SellCategory first  = new SellCategory("first", "First", new BigDecimal("5"), List.of(firstTemplate));
		SellCategory second = new SellCategory("second", "Second", new BigDecimal("99"), List.of(secondTemplate));
		ShopDefinition definition = new ShopDefinition("shop", "Shop", 54, List.of(), List.of(),
		                                               List.of(first, second));

		ItemValuation result = valuator.value(definition, new ItemStack(Material.DIAMOND, 1), 1.0, 1.0);

		assertEquals("first", result.categoryId());
		assertEquals(new BigDecimal("5.00"), result.unitPrice());
	}

}
