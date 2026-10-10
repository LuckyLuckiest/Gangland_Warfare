package org.luckyraven.gangland.turf.powerups;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves {@link PowerupRegistry#replaceAll(Map)} is a defensive, atomic swap: mutating the caller's source map
 * after the call must never bleed into the registry, and readers only ever see one full generation at a time. Ties
 * to the "PowerupRegistry.replaceAll atomicity/defensive copying" bullet in turf.md's Test Surface section.
 */
@DisplayName("PowerupRegistry — replaceAll defensive copy and lookups")
class PowerupRegistryTest {

	private static PowerupDefinition def(String id) {
		return new PowerupDefinition(id, "&a" + id, BigDecimal.valueOf(100), EffectType.INCOME_MULTIPLIER, 1.1, 60);
	}

	@Test
	@DisplayName("get/exists/ids/all are empty before any replaceAll has been called")
	void freshRegistry_isEmpty() {
		PowerupRegistry registry = new PowerupRegistry();

		assertNull(registry.get("anything"));
		assertFalse(registry.exists("anything"));
		assertTrue(registry.all().isEmpty());
		assertTrue(registry.ids().isEmpty());
	}

	@Test
	@DisplayName("replaceAll makes every entry visible through get/exists/ids/all")
	void replaceAll_populatesLookups() {
		PowerupRegistry registry = new PowerupRegistry();
		Map<String, PowerupDefinition> source = new LinkedHashMap<>();
		source.put("small_income_boost", def("small_income_boost"));
		source.put("garrison_discount", def("garrison_discount"));

		registry.replaceAll(source);

		assertEquals(2, registry.all().size());
		assertTrue(registry.exists("small_income_boost"));
		assertEquals(Set_of("garrison_discount", "small_income_boost"), registry.ids());
		assertEquals("small_income_boost", registry.get("small_income_boost").id());
	}

	@Test
	@DisplayName("mutating the caller's map after replaceAll does not affect the registry")
	void replaceAll_defendsAgainstLaterMutationOfTheSourceMap() {
		PowerupRegistry registry = new PowerupRegistry();
		Map<String, PowerupDefinition> source = new LinkedHashMap<>();
		source.put("small_income_boost", def("small_income_boost"));

		registry.replaceAll(source);
		source.put("large_income_boost", def("large_income_boost")); // mutate after the fact
		source.remove("small_income_boost");

		assertTrue(registry.exists("small_income_boost"), "the registry's copy is unaffected by the later add");
		assertFalse(registry.exists("large_income_boost"), "the registry never saw the later addition");
	}

	@Test
	@DisplayName("a second replaceAll fully replaces the first generation, not merges with it")
	void replaceAll_fullyReplacesThePreviousGeneration() {
		PowerupRegistry registry = new PowerupRegistry();
		Map<String, PowerupDefinition> first = new LinkedHashMap<>();
		first.put("old_entry", def("old_entry"));
		registry.replaceAll(first);

		Map<String, PowerupDefinition> second = new LinkedHashMap<>();
		second.put("new_entry", def("new_entry"));
		registry.replaceAll(second);

		assertFalse(registry.exists("old_entry"), "the old generation is gone entirely, not merged");
		assertTrue(registry.exists("new_entry"));
		assertEquals(1, registry.all().size());
	}

	@Test
	@DisplayName("W2-Q: a reinforced_defense entry from an old file is skipped, the rest of the catalogue loads (D4)")
	void loader_skipsReinforcedDefense() throws Exception {
		PowerupRegistry registry = load("Powerups:\n"
		                                + "   reinforced_defense:\n"
		                                + "      Display_Name: \"&cReinforced\"\n"
		                                + "      Cost: 8000\n"
		                                + "      Effect_Type: CAPTURE_DEFENSE_BONUS\n"
		                                + "      Magnitude: 1.0\n"
		                                + "      Duration_Seconds: 1800\n"
		                                + "   small_income_boost:\n"
		                                + "      Display_Name: \"&aSmall\"\n"
		                                + "      Cost: 300\n"
		                                + "      Effect_Type: INCOME_MULTIPLIER\n"
		                                + "      Magnitude: 1.25\n"
		                                + "      Duration_Seconds: 3600\n");

		assertEquals(Set_of("small_income_boost"), registry.ids());
	}

	@Test
	@DisplayName("W2-Q: the shipped catalogue has no reinforced_defense and prices the boosts 300 and 1000 (D4, D9)")
	void shippedCatalogue_hasNoReinforcedDefense() throws Exception {
		PowerupRegistry registry = load(YamlFixture.shipped("/turf/turf_powerups.yml"));

		assertFalse(registry.exists("reinforced_defense"));
		assertTrue(registry.exists("garrison_discount"));
		assertEquals(0, new BigDecimal("300").compareTo(registry.get("small_income_boost").cost()));
		assertEquals(0, new BigDecimal("1000").compareTo(registry.get("large_income_boost").cost()));
	}

	@Test
	@DisplayName("W2-Q: Garrison.Cost_Per_Guard is read from the YAML (TF-17)")
	void garrisonCost_fromYaml() throws Exception {
		PowerupRegistry registry = load("Garrison:\n   Cost_Per_Guard: 1234\n"
		                                + "Powerups:\n   a:\n"
		                                + "      Cost: 1\n      Effect_Type: INCOME_MULTIPLIER\n"
		                                + "      Magnitude: 1.1\n      Duration_Seconds: 60\n");

		assertEquals(0, new BigDecimal("1234").compareTo(registry.garrisonCost()));
	}

	@Test
	@DisplayName("W2-Q: a missing Garrison section keeps the 1000 guard price (TF-17)")
	void garrisonCost_defaultsTo1000() throws Exception {
		PowerupRegistry registry = load("Powerups:\n   a:\n      Cost: 1\n      Effect_Type: INCOME_MULTIPLIER\n"
		                                + "      Magnitude: 1.1\n      Duration_Seconds: 60\n");

		assertEquals(0, new BigDecimal("1000").compareTo(registry.garrisonCost()));
	}

	private static PowerupRegistry load(String yaml) throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(
				new ConfigParser().parse(Path.of("turf_powerups.yml"), new StringReader(yaml), new ConfigReport()));
		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("turf_powerups")).thenReturn(handler);

		PowerupRegistry registry = new PowerupRegistry();
		new PowerupRegistryLoader(registry, fileManager).onInitialize(true);
		return registry;
	}

	private static java.util.Set<String> Set_of(String... values) {
		return new java.util.TreeSet<>(java.util.Arrays.asList(values));
	}
}
