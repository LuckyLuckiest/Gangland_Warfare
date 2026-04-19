package me.luckyraven.item.dsl;

import me.luckyraven.item.ItemConverter;
import me.luckyraven.item.ItemConverterRegistry;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.SourceLocation;
import me.luckyraven.persistence.config.dsl.BracketedAttrsParser;
import me.luckyraven.persistence.config.dsl.DslValue;
import me.luckyraven.persistence.config.dsl.StringDslParser;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ItemDslAdapter}.
 *
 * <p>Uses a hand-rolled {@link ItemConverter} (no Mockito) so the test stays
 * compile-only on Spigot API and records the exact type/modifier/attrs that reached the converter.
 */
@DisplayName("ItemDslAdapter — DSL → ItemStack bridge")
class ItemDslAdapterTest {

	private static final Path           FIXTURE = Path.of("shops.yml");
	private static final SourceLocation AT_5_3  = new SourceLocation(FIXTURE, 5, 3, 5, 3);

	private RecordingConverter    converter;
	private ItemConverterRegistry registry;
	private ItemDslAdapter        adapter;

	@BeforeEach
	void setUp() {
		converter = new RecordingConverter();
		registry  = new ItemConverterRegistry();
		registry.register("weapon", converter);
		registry.register("material", converter);

		adapter = new ItemDslAdapter(registry, new BracketedAttrsParser());
	}

	@Test
	@DisplayName("apply — registered type + attributes dispatches to converter")
	void apply_registeredType() {
		DslValue value = new DslValue("weapon:ak47", Map.of("name", DslValue.leaf("Gold", AT_5_3)),
		                              "weapon:ak47[name=Gold]", AT_5_3);

		ConfigReport report = new ConfigReport();
		ItemStack    stack  = adapter.apply(value, report);

		assertNotNull(stack);
		assertEquals("weapon", converter.lastType);
		assertEquals("ak47", converter.lastModifier);
		assertEquals(Map.of("name", "Gold"), converter.lastAttrs);
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("apply — bare Material name falls back to the 'material' converter")
	void apply_materialFallback() {
		DslValue value = new DslValue("DIAMOND_SWORD", Collections.emptyMap(), "DIAMOND_SWORD", AT_5_3);

		ConfigReport report = new ConfigReport();
		adapter.apply(value, report);

		assertEquals("DIAMOND_SWORD", converter.lastType);
		assertNull(converter.lastModifier);
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("apply — unknown type records item.unknown_type at the DSL location")
	void apply_unknownType_recordsLocated() {
		DslValue value = new DslValue("NOT_A_REAL_TYPE", Collections.emptyMap(), "NOT_A_REAL_TYPE", AT_5_3);

		ConfigReport report = new ConfigReport();
		ItemStack    stack  = adapter.apply(value, report);

		assertNull(stack);
		assertTrue(report.hasErrors());
		assertEquals("item.unknown_type", report.issues().getFirst().code());
		assertEquals(5, report.issues().getFirst().at().line());
	}

	@Test
	@DisplayName("apply — converter returning null records item.conversion_failed")
	void apply_converterReturnsNull_recordsFailure() {
		converter.returnNull = true;

		DslValue value = new DslValue("weapon:broken", Collections.emptyMap(), "weapon:broken", AT_5_3);

		ConfigReport report = new ConfigReport();
		ItemStack    stack  = adapter.apply(value, report);

		assertNull(stack);
		assertTrue(report.issues()
						   .stream().anyMatch(i -> i.code().equals("item.conversion_failed")));
	}

	@Test
	@DisplayName("apply — null / empty head records item.missing_type")
	void apply_missingHead_records() {
		DslValue value = new DslValue(null, Collections.emptyMap(), "{}", AT_5_3);

		ConfigReport report = new ConfigReport();
		ItemStack    stack  = adapter.apply(value, report);

		assertNull(stack);
		assertEquals("item.missing_type", report.issues().getFirst().code());
	}

	@Test
	@DisplayName("asDslParser — parses raw scalar through BracketedAttrsParser, then applies")
	void asDslParser_roundTrip() {
		StringDslParser<ItemStack> parser = adapter.asDslParser();

		ConfigReport report = new ConfigReport();
		ItemStack    stack  = parser.parse("weapon:ak47{name=Gold}", AT_5_3, report);

		assertNotNull(stack);
		assertEquals("weapon", converter.lastType);
		assertEquals("ak47", converter.lastModifier);
		assertEquals(Map.of("name", "Gold"), converter.lastAttrs);
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("asDslParser — propagates DSL syntax errors from the underlying parser")
	void asDslParser_syntaxError_propagates() {
		StringDslParser<ItemStack> parser = adapter.asDslParser();

		ConfigReport report = new ConfigReport();
		parser.parse("weapon:ak47[name]", AT_5_3, report);

		assertTrue(report.issues()
						   .stream().anyMatch(i -> i.code().equals("dsl.syntax")));
	}

	// -------------------------------------------------------------- helpers

	private static final class RecordingConverter implements ItemConverter {

		String              lastType;
		String              lastModifier;
		Map<String, String> lastAttrs;
		boolean             returnNull;

		@Override
		public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
			this.lastType     = type;
			this.lastModifier = modifier;
			this.lastAttrs    = attributes;

			if (returnNull) return null;

			return new ItemStack(org.bukkit.Material.STONE);
		}

	}

}
