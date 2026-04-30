package org.luckyraven.gangland.persistence.config.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.persistence.config.ConfigReport;
import org.luckyraven.gangland.persistence.config.SourceLocation;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BracketedAttrsParser}.
 *
 * <p>Anchors every input to a synthetic scalar location of {@code line=12, col=5}
 * and asserts that sub-token positions are computed as {@code col = 5 + offset}.
 */
@DisplayName("BracketedAttrsParser — item[...] style DSL")
class BracketedAttrsParserTest {

	private static final Path           FIXTURE = Path.of("traits.yml");
	private static final SourceLocation AT_12_5 = new SourceLocation(FIXTURE, 12, 5, 12, 5);

	private final BracketedAttrsParser parser = new BracketedAttrsParser();

	@Test
	@DisplayName("worked example — item[custom_model_data=1021,effects={}]")
	void workedExample_positions() {
		ConfigReport report = new ConfigReport();
		String       raw    = "item[custom_model_data=1021,effects={}]";

		DslValue root = parser.parse(raw, AT_12_5, report);

		assertFalse(report.hasErrors(), "valid input should parse cleanly");
		assertNotNull(root);
		assertEquals("item", root.name());
		assertEquals(12, root.at().line());
		assertEquals(5, root.at().column());

		DslValue cmd = root.attr("custom_model_data");
		assertNotNull(cmd);
		assertEquals("1021", cmd.raw());
		assertEquals(5 + 23, cmd.at().column(), "'1021' starts at offset 23 within the scalar");

		DslValue effects = root.attr("effects");
		assertNotNull(effects);
		assertNull(effects.name(), "nested {} has no head name");
		assertTrue(effects.attrs().isEmpty());
	}

	@Test
	@DisplayName("invalid int — position points at the offending value inside the scalar")
	void invalidInt_positionOffset() {
		ConfigReport report = new ConfigReport();
		String       raw    = "item[custom_model_data=abc]";

		DslValue root = parser.parse(raw, AT_12_5, report);

		DslValue cmd = root.attr("custom_model_data");
		assertEquals("abc", cmd.raw());
		assertEquals(5 + 23, cmd.at().column(), "'abc' starts at same offset as '1021' in the earlier test");
	}

	@Test
	@DisplayName("bare leaf — no brackets, produces leaf DslValue")
	void bareLeaf_leafValue() {
		ConfigReport report = new ConfigReport();

		DslValue value = parser.parse("DIAMOND_SWORD", AT_12_5, report);

		assertFalse(report.hasErrors());
		assertNull(value.name(), "leaf DslValue has no head name");
		assertEquals("DIAMOND_SWORD", value.raw());
		assertTrue(value.attrs().isEmpty());
	}

	@Test
	@DisplayName("nested braces — recurses into attribute value")
	void nested_recurses() {
		ConfigReport report = new ConfigReport();
		String       raw    = "item[effects={fire=true,duration=5}]";

		DslValue root    = parser.parse(raw, AT_12_5, report);
		DslValue effects = root.attr("effects");

		assertNotNull(effects);
		assertEquals(2, effects.attrs().size());
		assertEquals("true", effects.attr("fire").raw());
		assertEquals("5", effects.attr("duration").raw());
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("missing '=' — records dsl.syntax ERROR, recovers at next terminator")
	void missingEquals_reportsSyntax() {
		ConfigReport report = new ConfigReport();
		String       raw    = "item[custom_model_data,effects={}]";

		parser.parse(raw, AT_12_5, report);

		assertTrue(report.hasErrors());
		assertEquals("dsl.syntax", report.issues().getFirst().code());
	}

	@Test
	@DisplayName("unclosed bracket — records dsl.unclosed")
	void unclosedBracket_reports() {
		ConfigReport report = new ConfigReport();
		String       raw    = "item[custom_model_data=1021";

		parser.parse(raw, AT_12_5, report);

		assertTrue(report.issues()
						   .stream().anyMatch(i -> i.code().equals("dsl.unclosed")));
	}

	@Test
	@DisplayName("duplicate attribute — WARNING, last value wins")
	void duplicateAttr_warns() {
		ConfigReport report = new ConfigReport();
		String       raw    = "item[custom_model_data=1,custom_model_data=2]";

		DslValue root = parser.parse(raw, AT_12_5, report);

		assertEquals("2", root.attr("custom_model_data").raw());
		assertTrue(report.issues()
						   .stream().anyMatch(i -> i.code().equals("dsl.duplicate")));
	}

	@Test
	@DisplayName("null input — returns null without throwing")
	void nullInput_returnsNull() {
		ConfigReport report = new ConfigReport();

		assertNull(parser.parse(null, AT_12_5, report));
	}

}
