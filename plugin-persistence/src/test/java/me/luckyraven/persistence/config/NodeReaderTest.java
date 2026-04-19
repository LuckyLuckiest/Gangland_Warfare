package me.luckyraven.persistence.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NodeReader}.
 *
 * <p>Builds a small YAML fixture via {@link ConfigParser} and reads it through
 * {@code NodeReader}, asserting both returned values and the issues that land in {@link ConfigReport}.
 */
@DisplayName("NodeReader — typed accessors and validators")
class NodeReaderTest {

	private static final Path FIXTURE = Path.of("traits.yml");

	private ConfigReport report;
	private MappingNode  root;

	@Test
	@DisplayName("asString().orDefault — missing key returns default, no error")
	void string_missing_orDefault() {
		loadYaml("Cost: 500\n");

		NodeReader reader = NodeReader.of(root, report);
		String     value  = reader.get("Display_Name").asString().orDefault("Fallback");

		assertEquals("Fallback", value);
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("asString().required — missing key records config.required with parent location")
	void string_missing_required() {
		loadYaml("Cost: 500\n");

		NodeReader reader = NodeReader.of(root, report);
		reader.get("Display_Name").asString().required();

		assertTrue(report.hasErrors());
		ConfigIssue issue = report.issues().getFirst();
		assertEquals("config.required", issue.code());
		assertEquals("Display_Name", issue.path());
	}

	@Test
	@DisplayName("asInt — non-numeric value records config.int with scalar location")
	void int_wrongValue_reportsWithLocation() {
		loadYaml("""
						 Display_Name: Smiley
						 Cost: not-a-number
						 """);

		NodeReader reader = NodeReader.of(root, report);
		int        value  = reader.get("Cost").asInt().required().orDefault(0);

		assertEquals(0, value);
		assertTrue(report.hasErrors());

		ConfigIssue issue = report.issues().getFirst();
		assertEquals("config.int", issue.code());
		assertEquals("Cost", issue.path());
		assertEquals(2, issue.at().line(), "location should point at the Cost value on line 2");
	}

	@Test
	@DisplayName("asDouble().min — below minimum records config.range and invalidates")
	void double_belowMin_invalidates() {
		loadYaml("Sell_Price_Ratio: -0.5\n");

		NodeReader reader = NodeReader.of(root, report);
		double     value  = reader.get("Sell_Price_Ratio").asDouble().min(0).required().orDefault(1.0);

		assertEquals(1.0, value, "below-min coerces to default");
		assertTrue(report.hasErrors());
		assertEquals("config.range", report.issues().getFirst().code());
	}

	@Test
	@DisplayName("asBool — accepts true/false/yes/no/on/off case-insensitively")
	void bool_acceptsYamlTruthy() {
		loadYaml("""
						 A: yes
						 B: OFF
						 C: True
						 """);

		NodeReader reader = NodeReader.of(root, report);

		assertTrue(reader.get("A").asBool().orDefault(false));
		assertFalse(reader.get("B").asBool().orDefault(true));
		assertTrue(reader.get("C").asBool().orDefault(false));
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("asBool — garbage string records config.bool")
	void bool_garbage_reports() {
		loadYaml("A: maybe\n");

		NodeReader reader = NodeReader.of(root, report);
		boolean    value  = reader.get("A").asBool().orDefault(true);

		assertTrue(value);
		assertEquals("config.bool", report.issues().getFirst().code());
	}

	@Test
	@DisplayName("asList().ofStrings — returns items in order, empty when missing")
	void list_ofStrings_roundtrip() {
		loadYaml("""
						 Tags:
						   - alpha
						   - beta
						 """);

		NodeReader   reader  = NodeReader.of(root, report);
		List<String> tags    = reader.get("Tags").asList().ofStrings().orEmpty();
		List<String> missing = reader.get("Missing").asList().ofStrings().orEmpty();

		assertEquals(List.of("alpha", "beta"), tags);
		assertEquals(List.of(), missing);
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("asList().ofInts — non-int element reports config.int and is skipped")
	void list_ofInts_skipsInvalid() {
		loadYaml("""
						 Levels:
						   - 1
						   - zero
						   - 3
						 """);

		NodeReader    reader = NodeReader.of(root, report);
		List<Integer> levels = reader.get("Levels").asList().ofInts().orEmpty();

		assertEquals(List.of(1, 3), levels, "invalid element is dropped");
		assertTrue(report.issues()
						   .stream().anyMatch(i -> i.code().equals("config.int")));
	}

	@Test
	@DisplayName("asMapping — missing returns null via orNull; required records config.required")
	void mapping_missing() {
		loadYaml("Cost: 500\n");

		NodeReader  reader  = NodeReader.of(root, report);
		MappingNode profile = reader.get("Profile").asMapping().required().orNull();

		assertNull(profile);
		assertEquals("config.required", report.issues().getFirst().code());
	}

	@Test
	@DisplayName("nested mapping — child reader builds full dotted path in errors")
	void nestedReader_pathPrefixed() {
		loadYaml("""
						 smiley:
						   Cost: bad
						 """);

		NodeReader  root   = NodeReader.of(this.root, report);
		MappingNode smiley = root.get("smiley").asMapping().required().orNull();
		NodeReader  child  = NodeReader.of(smiley, report);

		child.get("Cost").asInt().required();

		assertTrue(report.issues()
						   .stream()
						   .anyMatch(i -> i.path().equals("smiley.Cost") && i.code().equals("config.int")));
	}

	private void loadYaml(String yaml) {
		report = new ConfigReport();
		ConfigDocument doc = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);
		root = doc.root();
	}

}
