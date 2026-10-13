package org.luckyraven.gangland.copsncrooks.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerSettings;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lane f2-config: {@link CopsItems} builds the Police Scanner and Scanner Map from the loaded config, marks them with
 * a persistent-data marker, and recognises only its own items (P-SCN-3, P-MAP-3).
 */
@DisplayName("CopsItems - police scanner and scanner map")
class CopsItemsTest {

	private ItemFixture                           fixture;
	private AtomicReference<CopConfigProvider>    config;
	private CopsItems                             items;

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		fixture = new ItemFixture();
		config  = new AtomicReference<>(provider(new ScannerSettings(true, 160.0, "CLOCK", "&9Police Scanner",
		                                                             List.of("&7Hear the police radio")),
		                                         new ScannerMapSettings(true, 1, 10, true, "&9Scanner Map",
		                                                                List.of("&7Cops, search zone, hideouts"))));
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getName()).thenReturn("gangland");
		items = new CopsItems(plugin, config::get);
	}

	@AfterEach
	void tearDown() {
		fixture.close();
	}

	@Test
	@DisplayName("the ids are police_scanner and scanner_map, in that order")
	void ids() {
		assertEquals(List.of("police_scanner", "scanner_map"), CopsItems.ids());
	}

	@Test
	@DisplayName("a created scanner is marked as a scanner and not as a scanner map; a plain clock is neither")
	void scannerItemsAreMarked() {
		ItemStack scanner = items.create("police_scanner");

		assertNotNull(scanner);
		assertEquals(Material.CLOCK, scanner.getType());
		assertTrue(items.isScanner(scanner));
		assertFalse(items.isScannerMap(scanner));
		assertFalse(items.isScanner(new ItemStack(Material.CLOCK)));
	}

	@Test
	@DisplayName("a created scanner map is a filled map marked as a scanner map and not as a scanner")
	void scannerMapItemsAreMarked() {
		ItemStack map = items.create("scanner_map");

		assertNotNull(map);
		assertEquals(Material.FILLED_MAP, map.getType());
		assertTrue(items.isScannerMap(map));
		assertFalse(items.isScanner(map));
		assertFalse(items.isScannerMap(new ItemStack(Material.FILLED_MAP)));
	}

	@Test
	@DisplayName("null, air and unmarked items are neither (no exception)")
	void nullAndAir_areNotMarked() {
		assertFalse(items.isScanner(null));
		assertFalse(items.isScannerMap(null));
		assertFalse(items.isScanner(new ItemStack(Material.AIR)));
		assertFalse(items.isScannerMap(new ItemStack(Material.AIR)));
	}

	@Test
	@DisplayName("ids are case-insensitive; an unknown id is null")
	void ids_caseInsensitive_unknownIsNull() {
		assertTrue(items.isScanner(items.create("POLICE_Scanner")));
		assertNull(items.create("jetpack"));
		assertNull(items.create(""));
	}

	@Test
	@DisplayName("every create is a fresh stack")
	void create_isFresh() {
		assertNotSame(items.create("police_scanner"), items.create("police_scanner"));
	}

	@Test
	@DisplayName("material, name and lore come from the config loaded now: a reload shows on the next item")
	void configIsReadAtCallTime() {
		ItemStack before = items.create("police_scanner");
		assertTrue(before.getItemMeta().getDisplayName().contains("Police Scanner"));
		assertTrue(before.getItemMeta().getLore().stream().anyMatch(line -> line.contains("Hear the police radio")));

		config.set(provider(new ScannerSettings(true, 99.0, "COMPASS", "&cBeat Radio", List.of("&7custom")),
		                    new ScannerMapSettings(true, 1, 10, true, "&aMap Two", List.of("&7map lore"))));

		ItemStack scanner = items.create("police_scanner");
		assertEquals(Material.COMPASS, scanner.getType());
		assertTrue(scanner.getItemMeta().getDisplayName().contains("Beat Radio"));
		assertTrue(items.isScanner(scanner));
		assertTrue(items.create("scanner_map").getItemMeta().getDisplayName().contains("Map Two"));
	}

	@Test
	@DisplayName("a material the server does not know falls back to CLOCK instead of failing")
	void badMaterial_fallsBackToClock() {
		config.set(provider(new ScannerSettings(true, 160.0, "NOT_A_MATERIAL", "&9X", List.of()),
		                    ScannerMapSettings.DEFAULT));

		assertEquals(Material.CLOCK, items.create("police_scanner").getType());
	}

	@Test
	@DisplayName("before the first config load (null provider) the shipped defaults apply")
	void nullProvider_usesShippedDefaults() {
		config.set(null);

		ItemStack scanner = items.create("police_scanner");

		assertEquals(Material.CLOCK, scanner.getType());
		assertTrue(scanner.getItemMeta().getDisplayName().contains("Police Scanner"));
		assertTrue(items.isScanner(scanner));
	}

	private static CopConfigProvider provider(ScannerSettings scanner, ScannerMapSettings map) {
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getScannerSettings()).thenReturn(scanner);
		when(provider.getScannerMapSettings()).thenReturn(map);
		return provider;
	}
}
