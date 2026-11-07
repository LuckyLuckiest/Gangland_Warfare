package org.luckyraven.gangland.lootchest.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.lootchest.LootChestService;
import org.luckyraven.gangland.lootchest.data.LootTable;
import org.luckyraven.gangland.lootchest.item.LootItemReference.Rarity;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how {@link LootChestLoader} reads the per-rarity chances twice: the global {@code Rarity} section of tiers.yml
 * and the per-table {@code Rarity_Overrides} of loot_chests.yml. Both read the lower-cased constant names
 * ({@code common}, {@code uncommon}, {@code rare}, {@code epic}, {@code legendary}), skip an absent key, and read a
 * present key as a double defaulting to 0.0.
 */
@DisplayName("LootChestLoader - Rarity and Rarity_Overrides")
class LootChestLoaderRarityTest {

	private static String yaml(String... lines) {
		return String.join("\n", lines) + "\n";
	}

	private static final String ONE_ITEM = yaml("      Items:", "         a:", "            Item: \"STONE\"");

	private static LootChestConfig load(String tiersYaml, String lootChestsYaml) throws IOException {
		FileManager fileManager = mock(FileManager.class);
		FileHandler lootChests = handler("loot_chests.yml", lootChestsYaml);
		FileHandler tiers      = handler("tiers.yml", tiersYaml);
		when(fileManager.getFile("loot_chests")).thenReturn(lootChests);
		when(fileManager.getFile("tiers")).thenReturn(tiers);

		LootChestLoader loader = new LootChestLoader(mock(JavaPlugin.class), mock(LootChestService.class),
		                                             mock(LootChestSettingsProvider.class), false, null, fileManager);
		loader.load(false, null, fileManager);

		LootChestConfig config = loader.getLoadedConfig();
		assertNotNull(config);
		return config;
	}

	private static FileHandler handler(String name, String yaml) {
		ConfigDocument document = new ConfigParser().parse(Path.of(name), new StringReader(yaml), new ConfigReport());
		FileHandler    handler  = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(document);
		return handler;
	}

	private static List<Rarity> order(Map<Rarity, Double> chances) {
		return new ArrayList<>(chances.keySet());
	}

	@Test
	@DisplayName("the global Rarity section reads the keys present, in enum order, a non-number as 0.0, and skips the absent ones")
	void globalRarity_readsPresentKeysOnly() throws IOException {
		LootChestConfig config = load(yaml("Rarity:", "   legendary: oops", "   rare: 0.25", "   common: 0.9"), "");

		Map<Rarity, Double> chances = config.getGlobalRarityChances();

		assertEquals(Map.of(Rarity.COMMON, 0.9, Rarity.RARE, 0.25, Rarity.LEGENDARY, 0.0), chances);
		assertEquals(List.of(Rarity.COMMON, Rarity.RARE, Rarity.LEGENDARY), order(chances),
		             "the map is an EnumMap, so it iterates in constant order whatever the file order");
	}

	@Test
	@DisplayName("a capitalised key is not the lower-cased constant name, so it is skipped")
	void globalRarity_keysAreMatchedCaseSensitively() throws IOException {
		LootChestConfig config = load(yaml("Rarity:", "   Common: 0.9", "   epic: 0.1"), "");

		assertEquals(Map.of(Rarity.EPIC, 0.1), config.getGlobalRarityChances());
	}

	@Test
	@DisplayName("a tiers file without a Rarity section gives no global chances")
	void globalRarity_missingSectionIsEmpty() throws IOException {
		LootChestConfig config = load(yaml("Tiers:", "   gold:", "      Display_Name: \"Gold\""), "");

		assertTrue(config.getGlobalRarityChances().isEmpty());
	}

	@Test
	@DisplayName("a table starts from the global chances and Rarity_Overrides replaces only the keys it names")
	void tableOverrides_layerOnTopOfTheGlobalChances() throws IOException {
		LootChestConfig config = load(yaml("Rarity:", "   common: 0.9", "   rare: 0.25", "   legendary: nothing"),
		                              yaml("Loot_Tables:", "   layered:") + ONE_ITEM +
		                              yaml("      Rarity_Overrides:", "         common: 0.5", "         epic: 0.1",
		                                   "         uncommon: broken", "   untouched:") + ONE_ITEM +
		                              yaml("   emptyOverrides:") + ONE_ITEM +
		                              yaml("      Rarity_Overrides:", "         Common: 0.5"));

		Map<String, LootTable> tables = config.getLootTables();

		Map<Rarity, Double> layered = tables.get("layered").getRarityOverrides();
		assertEquals(Map.of(Rarity.COMMON, 0.5, Rarity.UNCOMMON, 0.0, Rarity.RARE, 0.25, Rarity.EPIC, 0.1,
		                    Rarity.LEGENDARY, 0.0), layered);
		assertEquals(List.of(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY),
		             order(layered));

		assertEquals(config.getGlobalRarityChances(), tables.get("untouched").getRarityOverrides(),
		             "no Rarity_Overrides section: a copy of the global chances");
		assertEquals(config.getGlobalRarityChances(), tables.get("emptyOverrides").getRarityOverrides(),
		             "a section whose only key is capitalised changes nothing");
	}
}
