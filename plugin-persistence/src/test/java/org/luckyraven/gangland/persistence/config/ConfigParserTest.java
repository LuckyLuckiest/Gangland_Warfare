package org.luckyraven.gangland.persistence.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigParser}.
 *
 * <p>Pure Java — no filesystem, YAML fed via {@link StringReader}.
 */
@DisplayName("ConfigParser — positional YAML parsing")
class ConfigParserTest {

	private static final Path FIXTURE = Path.of("fixture.yml");

	@Test
	@DisplayName("flat mapping — each scalar carries its 1-indexed source position")
	void flatMapping_scalarsCarryPositions() {
		String yaml = """
				Display_Name: Smiley
				Cost: 500
				""";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);

		assertTrue(report.isEmpty(), "flat YAML should parse clean");

		ScalarNode name = (ScalarNode) doc.root().get("Display_Name");
		ScalarNode cost = (ScalarNode) doc.root().get("Cost");

		assertEquals("Smiley", name.value());
		assertEquals(1, name.location().line());

		assertEquals("500", cost.value());
		assertEquals(2, cost.location().line());
	}

	@Test
	@DisplayName("nested mapping — child path tracks parent")
	void nested_childPath() {
		String yaml = """
				smiley:
				  Display_Name: Smiley
				  Sell_Price_Ratio: 0.8
				""";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);

		MappingNode smiley = (MappingNode) doc.root().get("smiley");
		ScalarNode  ratio  = (ScalarNode) smiley.get("Sell_Price_Ratio");

		assertEquals("smiley", smiley.path());
		assertEquals("smiley.Sell_Price_Ratio", ratio.path());
		assertEquals(3, ratio.location().line());
	}

	@Test
	@DisplayName("sequence — elements use bracketed index path")
	void sequence_bracketedPath() {
		String yaml = """
				Tags:
				  - alpha
				  - beta
				""";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);

		SequenceNode tags = (SequenceNode) doc.root().get("Tags");

		assertEquals(2, tags.items().size());
		assertEquals("Tags[0]", tags.items().get(0).path());
		assertEquals("Tags[1]", tags.items().get(1).path());
	}

	@Test
	@DisplayName("malformed YAML — emits a yaml.parse ERROR; returns empty document")
	void malformedYaml_reportsError() {
		String yaml = """
				Display_Name: "unterminated
				Cost: 500
				""";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);

		assertTrue(report.hasErrors(), "malformed YAML should produce an error");

		ConfigIssue issue = report.issues().getFirst();
		assertEquals(Severity.ERROR, issue.severity());
		assertEquals("yaml.parse", issue.code());
		assertNotNull(issue.at(), "issue must carry a location");

		assertTrue(doc.root().entries().isEmpty(),
		           "failed parse must return an empty root to keep readers safe");
	}

	@Test
	@DisplayName("empty document — root is an empty mapping, no issues")
	void emptyDocument_emptyRoot() {
		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(""), report);

		assertTrue(report.isEmpty());
		assertTrue(doc.root().entries().isEmpty());
	}

	@Test
	@DisplayName("explicit YAML null — value node is a NullNode")
	void explicitNull_producesNullNode() {
		String yaml = "Barter: ~\n";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);

		ConfigNode barter = doc.root().get("Barter");

		assertInstanceOf(NullNode.class, barter);
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("non-mapping top level — reports yaml.top_level ERROR")
	void nonMappingTopLevel_reportsError() {
		String yaml = "- one\n- two\n";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);

		assertTrue(report.hasErrors());
		assertEquals("yaml.top_level", report.issues().getFirst().code());
		assertTrue(doc.root().entries().isEmpty());
	}

	@Test
	@DisplayName("duplicate key — WARNING issued, later value wins")
	void duplicateKey_warns() {
		String yaml = """
				Cost: 100
				Cost: 200
				""";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(yaml), report);

		ScalarNode cost = (ScalarNode) doc.root().get("Cost");
		assertEquals("200", cost.value());

		assertTrue(report.issues()
						   .stream()
						   .anyMatch(i -> i.code().equals("yaml.duplicate") && i.severity() == Severity.WARNING));
	}

}
