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
import org.luckyraven.gangland.shop.BarterCategory;
import org.luckyraven.gangland.shop.ShopDefinition;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors {@link CategorySellValuatorTest} for the barter side. The key thing this suite pins beyond the sell mirror
 * is W22 (civilians-traders-shops.md): {@link CategoryBarterValuator} reads the exact same
 * {@code CategorySellValuator#SELL_PRICE_NBT_KEY} tag as the sell side, so a single admin-set per-item price applies
 * to both flows — there is no separate "barter price" tag.
 */
@DisplayName("CategoryBarterValuator — template matching, shared sell_price NBT key with the sell side")
class CategoryBarterValuatorTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	private RecordingNbtAccessor   nbt;
	private ItemSerializerRegistry serializers;
	private CategoryBarterValuator valuator;

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

		valuator = new CategoryBarterValuator(serializers);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private ShopDefinition definitionWithCategory(BarterCategory category) {
		return new ShopDefinition("shop", "Shop", 54, List.of(), List.of(), List.of(), List.of(category));
	}

	@Test
	@DisplayName("no matching category yields UNKNOWN")
	void value_noMatch_returnsUnknown() {
		BarterCategory category = new BarterCategory("junk", "Junk", new BigDecimal("40"),
		                                             List.of(new ItemStack(Material.COBBLESTONE, 1)));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.DIRT, 1), 1.0, 1.0);

		assertSame(ItemValuation.UNKNOWN, result);
	}

	@Test
	@DisplayName("without a tag, price is Base_Price divided by the template's own stack amount")
	void value_noNbtTag_usesBasePriceDividedByTemplateAmount() {
		ItemStack template = new ItemStack(Material.COBBLESTONE, 4);
		BarterCategory category = new BarterCategory("junk", "Junk", new BigDecimal("8"), List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.COBBLESTONE, 1), 1.0, 1.0);

		assertEquals(new BigDecimal("2.00"), result.unitPrice(), "8 / 4 template amount = 2 per item");
	}

	@Test
	@DisplayName("W22: a sell_price tag set by the SELL admin editor is honoured here too (shared NBT key)")
	void value_sharedSellPriceTag_appliesToBarterToo() {
		ItemStack template = new ItemStack(Material.COBBLESTONE, 4);
		// Written using the sell-side key constant, as SellCategoryItemsAdminView does for both flows.
		new ItemBuilder(template).addTag(CategorySellValuator.SELL_PRICE_NBT_KEY, "12");
		BarterCategory category = new BarterCategory("junk", "Junk", new BigDecimal("8"), List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.COBBLESTONE, 1), 1.0, 1.0);

		assertEquals(new BigDecimal("3.00"), result.unitPrice(), "12 (shared NBT tag) / 4 = 3 per item, not 8/4=2");
	}

	@Test
	@DisplayName("the result scales by barterPriceRatio * moodMultiplier")
	void value_scalesByBarterRatioAndMood() {
		ItemStack template = new ItemStack(Material.COBBLESTONE, 1);
		BarterCategory category = new BarterCategory("junk", "Junk", new BigDecimal("10"), List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		// 10.00 * (0.5 * 0.75 = 0.375 exact) = 3.75
		ItemValuation result = valuator.value(definition, new ItemStack(Material.COBBLESTONE, 1), 0.5, 0.75);

		assertEquals(new BigDecimal("3.75"), result.unitPrice());
	}

	@Test
	@DisplayName("a negative computed price clamps to zero")
	void value_negativeRatio_clampsToZero() {
		ItemStack template = new ItemStack(Material.COBBLESTONE, 1);
		BarterCategory category = new BarterCategory("junk", "Junk", new BigDecimal("10"), List.of(template));
		ShopDefinition definition = definitionWithCategory(category);

		ItemValuation result = valuator.value(definition, new ItemStack(Material.COBBLESTONE, 1), -1.0, 1.0);

		// Production clamps to a bare BigDecimal.ZERO (scale 0), while the non-clamped paths return scale 2,
		// so compare by value — BigDecimal.equals is scale-sensitive.
		assertEquals(0, result.unitPrice().compareTo(BigDecimal.ZERO), "a negative price clamps to zero");
	}

	@Test
	@DisplayName("a null definition or null stack returns UNKNOWN rather than throwing")
	void value_nullInputs_returnUnknown() {
		ShopDefinition definition = definitionWithCategory(BarterCategory.empty("junk"));

		assertSame(ItemValuation.UNKNOWN, valuator.value(null, new ItemStack(Material.DIRT, 1), 1.0, 1.0));
		assertSame(ItemValuation.UNKNOWN, valuator.value(definition, null, 1.0, 1.0));
	}

}
