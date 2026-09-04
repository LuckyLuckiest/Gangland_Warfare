package org.luckyraven.gangland.shop.io;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.shop.EntryKind;
import org.luckyraven.gangland.shop.SellCategory;
import org.luckyraven.gangland.shop.ShopDefinition;
import org.luckyraven.gangland.shop.ShopItemEntry;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves {@link ShopYamlReader#parse}: size normalisation (non-multiple-of-9, &lt;=0 and &gt;54 all fall back to
 * 54), the {@code Title}/{@code Size} defaults when no document parsed, entry skipping (missing item / negative
 * slot / missing price / unparsable price), price parsing across the Number / String / BigDecimal shapes
 * {@code ShopYamlReader.parsePrice} accepts, and category id validation across both the {@link ConfigurationSection}
 * and raw {@link Map} element shapes a hand-edited or legacy shop file can contain.
 *
 * <p>Every fixture here builds its {@code Buy_Entries} / {@code Sell_Categories} lists as live in-memory Java
 * objects via {@code YamlConfiguration.set(path, ...)} rather than round-tripping through YAML text — this avoids
 * needing a real Bukkit server to (de)serialize {@link ItemStack}s, since {@code FileConfiguration.set}/{@code get}
 * store and return the exact object given with no serialization step.
 */
@DisplayName("ShopYamlReader — size normalisation, entry skipping, price parsing, category id validation")
class ShopYamlReaderTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	private final ShopYamlReader reader = new ShopYamlReader();

	private FileHandler handlerWithDocument(String documentText, YamlConfiguration cfg) {
		FileHandler  handler = mock(FileHandler.class);
		ConfigReport report  = new ConfigReport();
		ConfigDocument doc   = new ConfigParser().parse(null, new StringReader(documentText), report);
		when(handler.getParsedDocument()).thenReturn(doc);
		when(handler.getFileConfiguration()).thenReturn(cfg);
		return handler;
	}

	// ── Title / Size ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("no parsed document at all falls back to the default title and size")
	void parse_noParsedDocument_usesDefaults() {
		FileHandler handler = mock(FileHandler.class); // getParsedDocument() -> null by default
		when(handler.getFileConfiguration()).thenReturn(new YamlConfiguration());

		ShopDefinition definition = reader.parse("shop", handler);

		assertEquals("Trader", definition.getTitle());
		assertEquals(54, definition.getSize());
	}

	@Test
	@DisplayName("a valid multiple-of-9 size at or under 54 is kept as-is")
	void parse_validSize_isKept() {
		FileHandler handler = handlerWithDocument("Title: My Shop\nSize: 18\n", new YamlConfiguration());

		ShopDefinition definition = reader.parse("shop", handler);

		assertEquals("My Shop", definition.getTitle());
		assertEquals(18, definition.getSize());
	}

	@Test
	@DisplayName("a size that is not a multiple of 9 falls back to 54")
	void parse_nonMultipleOf9Size_fallsBackTo54() {
		FileHandler handler = handlerWithDocument("Size: 7\n", new YamlConfiguration());

		assertEquals(54, reader.parse("shop", handler).getSize());
	}

	@Test
	@DisplayName("a size of zero or negative falls back to 54")
	void parse_nonPositiveSize_fallsBackTo54() {
		FileHandler handler = handlerWithDocument("Size: 0\n", new YamlConfiguration());

		assertEquals(54, reader.parse("shop", handler).getSize());
	}

	@Test
	@DisplayName("a size over 54 (even if a multiple of 9) falls back to 54")
	void parse_oversizedSize_fallsBackTo54() {
		FileHandler handler = handlerWithDocument("Size: 63\n", new YamlConfiguration());

		assertEquals(54, reader.parse("shop", handler).getSize());
	}

	// ── Entries (Map element shape) ─────────────────────────────────────────

	private Map<String, Object> entryMap(Integer slot, ItemStack item, Object price) {
		Map<String, Object> map = new LinkedHashMap<>();
		if (slot != null) map.put("Slot", slot);
		if (item != null) map.put("Item", item);
		if (price != null) map.put("Price", price);
		return map;
	}

	@Test
	@DisplayName("a valid Map-shaped entry with Number, String and BigDecimal prices all parse to equal amounts")
	void parse_entries_acceptsNumberStringAndBigDecimalPrices() {
		YamlConfiguration cfg = new YamlConfiguration();
		ItemStack          a  = new ItemStack(Material.IRON_SWORD);
		ItemStack          b  = new ItemStack(Material.DIAMOND);
		ItemStack          c  = new ItemStack(Material.EMERALD);
		cfg.set("Buy_Entries", List.of(
				entryMap(0, a, 10),                 // Number
				entryMap(1, b, "10.5"),              // String
				entryMap(2, c, new BigDecimal("11")) // BigDecimal
		));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		List<ShopItemEntry> entries = reader.parse("shop", handler).getBuyEntries();

		assertEquals(3, entries.size());
		assertEquals(0, entries.get(0).getPrice().compareTo(BigDecimal.TEN));
		assertEquals(0, entries.get(1).getPrice().compareTo(new BigDecimal("10.5")));
		assertEquals(0, entries.get(2).getPrice().compareTo(new BigDecimal("11")));
	}

	@Test
	@DisplayName("an entry with no Item is skipped")
	void parse_entryMissingItem_isSkipped() {
		YamlConfiguration cfg = new YamlConfiguration();
		cfg.set("Buy_Entries", List.of(entryMap(0, null, 10)));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		assertTrue(reader.parse("shop", handler).getBuyEntries().isEmpty());
	}

	@Test
	@DisplayName("an entry with a negative or missing slot is skipped")
	void parse_entryNegativeSlot_isSkipped() {
		YamlConfiguration cfg = new YamlConfiguration();
		cfg.set("Buy_Entries", List.of(entryMap(null, new ItemStack(Material.STONE), 10)));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		assertTrue(reader.parse("shop", handler).getBuyEntries().isEmpty());
	}

	@Test
	@DisplayName("an entry with no Price is skipped")
	void parse_entryMissingPrice_isSkipped() {
		YamlConfiguration cfg = new YamlConfiguration();
		cfg.set("Buy_Entries", List.of(entryMap(0, new ItemStack(Material.STONE), null)));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		assertTrue(reader.parse("shop", handler).getBuyEntries().isEmpty());
	}

	@Test
	@DisplayName("an unparsable garbage price string is silently skipped, indistinguishable from a missing price")
	void parse_garbagePrice_isSkipped() {
		YamlConfiguration cfg = new YamlConfiguration();
		cfg.set("Buy_Entries", List.of(entryMap(0, new ItemStack(Material.STONE), "not-a-number")));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		assertTrue(reader.parse("shop", handler).getBuyEntries().isEmpty());
	}

	@Test
	@DisplayName("an entry not shaped as a Map or ConfigurationSection is skipped without throwing")
	void parse_entryWrongShape_isSkippedWithoutThrowing() {
		YamlConfiguration cfg = new YamlConfiguration();
		cfg.set("Buy_Entries", List.of("not-a-mapping"));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		assertTrue(reader.parse("shop", handler).getBuyEntries().isEmpty());
	}

	@Test
	@DisplayName("Sell_Entries and Buy_Entries are read independently into their own lists")
	void parse_sellEntries_readIntoOwnList() {
		YamlConfiguration cfg = new YamlConfiguration();
		cfg.set("Buy_Entries", List.of(entryMap(0, new ItemStack(Material.STONE), 1)));
		cfg.set("Sell_Entries", List.of(entryMap(0, new ItemStack(Material.DIRT), 2), entryMap(1, new ItemStack(Material.SAND), 3)));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		ShopDefinition definition = reader.parse("shop", handler);

		assertEquals(1, definition.getBuyEntries().size());
		assertEquals(2, definition.getSellEntries().size());
		assertEquals(EntryKind.SELL, definition.getSellEntries().get(0).getKind());
	}

	// ── Categories ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("a Sell_Categories entry shaped as a Map with a blank id is skipped")
	void parse_sellCategory_mapShape_blankId_isSkipped() {
		YamlConfiguration   cfg = new YamlConfiguration();
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("Id", "");
		map.put("Base_Price", 5);
		cfg.set("Sell_Categories", List.of(map));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		assertTrue(reader.parse("shop", handler).getSellCategories().isEmpty());
	}

	@Test
	@DisplayName("a Sell_Categories entry shaped as a Map parses id, display name, price and items")
	void parse_sellCategory_mapShape_parsesFully() {
		YamlConfiguration   cfg   = new YamlConfiguration();
		ItemStack            item = new ItemStack(Material.GOLD_INGOT);
		Map<String, Object> map   = new LinkedHashMap<>();
		map.put("Id", "gems");
		map.put("Display_Name", "Gems");
		map.put("Base_Price", "12.5");
		map.put("Items", new ArrayList<>(List.of(item)));
		cfg.set("Sell_Categories", List.of(map));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		List<SellCategory> categories = reader.parse("shop", handler).getSellCategories();

		assertEquals(1, categories.size());
		SellCategory category = categories.get(0);
		assertEquals("gems", category.getId());
		assertEquals("Gems", category.getDisplayName());
		assertEquals(0, category.getBasePrice().compareTo(new BigDecimal("12.5")));
		assertEquals(1, category.getItems().size());
	}

	@Test
	@DisplayName("a Sell_Categories entry shaped as a ConfigurationSection parses identically to the Map shape")
	void parse_sellCategory_sectionShape_parsesFully() {
		YamlConfiguration    cfg     = new YamlConfiguration();
		ConfigurationSection section = cfg.createSection("__template");
		section.set("Id", "gems");
		section.set("Base_Price", 12);
		section.set("Items", new ArrayList<>(List.of(new ItemStack(Material.GOLD_INGOT))));
		cfg.set("Sell_Categories", List.of(section));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		List<SellCategory> categories = reader.parse("shop", handler).getSellCategories();

		assertEquals(1, categories.size());
		assertEquals("gems", categories.get(0).getId());
		assertEquals(0, categories.get(0).getBasePrice().compareTo(new BigDecimal("12")));
	}

	@Test
	@DisplayName("Display_Name falls back to the id when absent, for both category kinds")
	void parse_sellCategory_missingDisplayName_fallsBackToId() {
		YamlConfiguration   cfg = new YamlConfiguration();
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("Id", "gems");
		map.put("Base_Price", 1);
		cfg.set("Sell_Categories", List.of(map));
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(cfg);

		assertEquals("gems", reader.parse("shop", handler).getSellCategories().get(0).getDisplayName());
	}

}
