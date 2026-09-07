package org.luckyraven.gangland.turf.powerups;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

	private static java.util.Set<String> Set_of(String... values) {
		return new java.util.TreeSet<>(java.util.Arrays.asList(values));
	}
}
