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

	@Test
	@DisplayName("unknown key — produces WARNING with did-you-mean suggestion")
	void unknownKey_warnsWithSuggestion() {
		loadYaml("""
						 Maximum_Balance: 100
						 Maximun_Balance: 200
						 """);

		NodeReader reader = NodeReader.of(root, report);
		reader.get("Maximum_Balance").asInt().orDefault(0);

		ConfigIssue issue = report.issues()
				.stream()
				.filter(i -> i.code().equals("config.unknown_key"))
				.findFirst()
				.orElseThrow();

		assertEquals(Severity.WARNING, issue.severity());
		assertTrue(issue.message().contains("'Maximun_Balance'"));
		assertTrue(issue.message().contains("did you mean 'Maximum_Balance'?"),
		           "expected did-you-mean suggestion, got: " + issue.message());
	}

	@Test
	@DisplayName("Config_Version — never flagged even when no loader reads it")
	void configVersion_isGloballyExempt() {
		loadYaml("""
						 Config_Version: 3
						 Some_Key: value
						 """);

		NodeReader reader = NodeReader.of(root, report);
		reader.get("Some_Key").asString().orDefault("");

		assertTrue(report.issues()
						   .stream()
						   .noneMatch(
								   i -> i.code().equals("config.unknown_key") && i.path().endsWith("Config_Version")));
	}

	@Test
	@DisplayName("unknown key — no close match drops 'did you mean' clause")
	void unknownKey_noCloseMatch_noSuggestion() {
		loadYaml("""
						 Maximum_Balance: 100
						 Xylophone:       oops
						 """);

		NodeReader reader = NodeReader.of(root, report);
		reader.get("Maximum_Balance").asInt().orDefault(0);

		ConfigIssue issue = report.issues()
				.stream()
				.filter(i -> i.code().equals("config.unknown_key") && i.message().contains("'Xylophone'"))
				.findFirst()
				.orElseThrow();

		assertFalse(issue.message().contains("did you mean"),
		            "distance > 2 should suppress did-you-mean, got: " + issue.message());
	}

	@Test
	@DisplayName("unknown key in nested mapping — reports with child's dotted path")
	void unknownKey_nestedMapping_childPath() {
		loadYaml("""
						 Database:
						   MySQL:
						     Host:  localhost
						     Hoost: typo
						 """);

		NodeReader  root     = NodeReader.of(this.root, report);
		MappingNode database = root.get("Database").asMapping().required().orNull();
		NodeReader  dbReader = NodeReader.of(database, report);
		MappingNode mysql    = dbReader.get("MySQL").asMapping().required().orNull();
		NodeReader  mysqlR   = NodeReader.of(mysql, report);

		mysqlR.get("Host").asString().orDefault("");

		ConfigIssue issue = report.issues()
				.stream()
				.filter(i -> i.code().equals("config.unknown_key") && i.message().contains("'Hoost'"))
				.findFirst()
				.orElseThrow();

		assertEquals("Database.MySQL.Hoost", issue.path());
		assertTrue(issue.message().contains("did you mean 'Host'?"),
		           "expected did-you-mean 'Host', got: " + issue.message());
	}

	@Test
	@DisplayName("multiple readers over same mapping — touched sets union, no false positives")
	void multipleReaders_sameMapping_touchedSetsUnion() {
		loadYaml("""
						 Reload:
						   Cooldown:   20
						   Type:       instant
						   Sound:      noise
						   Action_Bar: msg
						 """);

		NodeReader  rootReader = NodeReader.of(root, report);
		MappingNode reload     = rootReader.get("Reload").asMapping().required().orNull();

		NodeReader readerA = NodeReader.of(reload, report);
		readerA.get("Cooldown").asInt().orDefault(0);
		readerA.get("Type").asString().orDefault("");

		NodeReader readerB = NodeReader.of(reload, report);
		readerB.get("Sound").asString().orDefault("");
		readerB.get("Action_Bar").asString().orDefault("");

		assertTrue(report.issues()
						   .stream()
						   .noneMatch(i -> i.code().equals("config.unknown_key")),
		           "every Reload key is read by one of the two readers, so no unknown_key should fire");
	}

	@Test
	@DisplayName("keys() — marks every key as touched, no unknown warnings")
	void keys_marksAllTouched() {
		loadYaml("""
						 alpha: 1
						 beta:  2
						 gamma: 3
						 """);

		NodeReader reader = NodeReader.of(root, report);
		for (String ignored : reader.keys()) {
			// iterate
		}

		assertTrue(report.issues()
						   .stream()
						   .noneMatch(i -> i.code().equals("config.unknown_key")));
	}

	private void loadYaml(String yaml) {
		report = new ConfigReport();
		ConfigDocument doc = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);
		root = doc.root();
	}

}
