package org.luckyraven.gangland.item.dsl;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.item.ItemConverter;
import org.luckyraven.gangland.item.ItemConverterRegistry;
import org.luckyraven.keystone.persistence.config.ConfigIssue;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.SourceLocation;
import org.luckyraven.keystone.persistence.config.dsl.BracketedAttrsParser;
import org.luckyraven.keystone.persistence.config.dsl.DslValue;
import org.luckyraven.keystone.persistence.config.dsl.StringDslParser;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

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
		assertEquals("item.unknown_type", report.issues().get(0).code());
		assertEquals(5, report.issues().get(0).at().line());
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
		assertEquals("item.missing_type", report.issues().get(0).code());
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

	@Test
	@DisplayName("apply — attribute the converter never reads records dsl.unknown_attr with suggestion")
	void apply_unknownAttribute_recordsWithSuggestion() {
		converter.readBehavior = attrs -> attrs.get("name");

		DslValue value = new DslValue("weapon:ak47",
		                              Map.of("name", DslValue.leaf("Gold", AT_5_3),
		                                     "namee", DslValue.leaf("Ghost", AT_5_3)),
		                              "weapon:ak47[name=Gold,namee=Ghost]", AT_5_3);

		ConfigReport report = new ConfigReport();
		adapter.apply(value, report);

		ConfigIssue issue = report.issues()
				.stream()
				.filter(i -> i.code().equals("dsl.unknown_attr"))
				.findFirst()
				.orElseThrow();

		assertTrue(issue.message().contains("'namee'"));
		assertTrue(issue.message().contains("did you mean 'name'?"),
		           "expected did-you-mean suggestion, got: " + issue.message());
	}

	@Test
	@DisplayName("apply — converter that reads nothing flags every supplied attribute")
	void apply_converterReadsNothing_flagsAll() {
		converter.readBehavior = attrs -> { };

		DslValue value = new DslValue("weapon:ak47",
		                              Map.of("name", DslValue.leaf("Gold", AT_5_3),
		                                     "tier", DslValue.leaf("Epic", AT_5_3)),
		                              "weapon:ak47[name=Gold,tier=Epic]", AT_5_3);

		ConfigReport report = new ConfigReport();
		adapter.apply(value, report);

		long unknownCount = report.issues()
				.stream()
				.filter(i -> i.code().equals("dsl.unknown_attr"))
				.count();

		assertEquals(2, unknownCount);
	}

	@Test
	@DisplayName("apply — converter iterating entrySet produces no unknown_attr warnings")
	void apply_converterIteratesEntrySet_noFalsePositives() {
		converter.readBehavior = attrs -> {
			for (var ignored : attrs.entrySet()) {
				// iteration counts as reading every key
			}
		};

		DslValue value = new DslValue("weapon:ak47",
		                              Map.of("name", DslValue.leaf("Gold", AT_5_3),
		                                     "tier", DslValue.leaf("Epic", AT_5_3)),
		                              "weapon:ak47[name=Gold,tier=Epic]", AT_5_3);

		ConfigReport report = new ConfigReport();
		adapter.apply(value, report);

		assertTrue(report.issues()
						   .stream()
						   .noneMatch(i -> i.code().equals("dsl.unknown_attr")));
	}

	@Test
	@DisplayName("apply — unknown attribute with no close match drops 'did you mean' clause")
	void apply_unknownAttribute_noCloseMatch_noSuggestion() {
		converter.readBehavior = attrs -> attrs.get("name");

		DslValue value = new DslValue("weapon:ak47",
		                              Map.of("name", DslValue.leaf("Gold", AT_5_3),
		                                     "xylophone", DslValue.leaf("?", AT_5_3)),
		                              "weapon:ak47[name=Gold,xylophone=?]", AT_5_3);

		ConfigReport report = new ConfigReport();
		adapter.apply(value, report);

		ConfigIssue issue = report.issues()
				.stream()
				.filter(i -> i.code().equals("dsl.unknown_attr"))
				.findFirst()
				.orElseThrow();

		assertTrue(issue.message().contains("'xylophone'"));
		assertFalse(issue.message().contains("did you mean"),
		            "distance > 2 should suppress did-you-mean, got: " + issue.message());
	}

	@Test
	@DisplayName("apply — converter returning null still emits unknown_attr warnings")
	void apply_converterReturnsNull_stillEmitsUnknownAttrs() {
		converter.returnNull   = true;
		converter.readBehavior = attrs -> { };

		DslValue value = new DslValue("weapon:broken",
		                              Map.of("extra", DslValue.leaf("ignored", AT_5_3)),
		                              "weapon:broken[extra=ignored]", AT_5_3);

		ConfigReport report = new ConfigReport();
		adapter.apply(value, report);

		assertTrue(report.issues()
						   .stream()
						   .anyMatch(i -> i.code().equals("dsl.unknown_attr")));
		assertTrue(report.issues()
						   .stream()
						   .anyMatch(i -> i.code().equals("item.conversion_failed")));
	}

	// -------------------------------------------------------------- helpers

	private static final class RecordingConverter implements ItemConverter {

		String                        lastType;
		String                        lastModifier;
		Map<String, String>           lastAttrs;
		boolean                       returnNull;
		Consumer<Map<String, String>> readBehavior;

		@Override
		public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
			this.lastType     = type;
			this.lastModifier = modifier;
			this.lastAttrs    = attributes;

			if (readBehavior != null) {
				readBehavior.accept(attributes);
			} else {
				// Default behavior mirrors a converter that consumes every supplied attribute — matches the legacy
				// contract where tests predating unknown-attr detection didn't care about read tracking.
				for (String key : attributes.keySet()) {
					attributes.get(key);
				}
			}

			if (returnNull) return null;

			return new ItemStack(org.bukkit.Material.STONE);
		}

	}

}
