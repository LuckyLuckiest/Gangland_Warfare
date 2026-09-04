package org.luckyraven.gangland.sign.parser;

import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.validation.SignValidationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link AbstractSignParser#parsePrice}/{@code #parseAmount}/{@code #parseContent} (Test
 * Surface, lootchests-signs-waypoints.md: "AbstractSignParser#parsePrice/parseAmount with money
 * symbols, thousands separators, color codes and negatives").
 *
 * <p>Test lives in the same package as {@code AbstractSignParser} so the protected parse methods
 * can be exercised directly. Per Observation #20 (lootchests-signs-waypoints.md): the parser —
 * unlike {@code AbstractSignValidator} — applies no sign or range check at all, so a sign whose
 * text bypassed {@code SignChangeEvent} validation (schematic, WorldEdit, another plugin) is
 * honoured with a negative price or a zero/negative amount. These tests pin that behaviour as it
 * stands today.
 */
@DisplayName("AbstractSignParser - price/amount/content parsing")
class AbstractSignParserTest {

	private static final SignType TYPE = new SignType("glw-buy", "buy");

	private final TestSignParser parser = new TestSignParser(TYPE);

	@Test
	@DisplayName("parsePrice strips the money symbol and thousands separators")
	void parsePrice_stripsMoneySymbolAndCommas() throws SignValidationException {
		assertEquals(1234.56, parser.parsePrice("$1,234.56", "$"), 1e-9);
	}

	@Test
	@DisplayName("parsePrice strips color codes before parsing")
	void parsePrice_stripsColorCodes() throws SignValidationException {
		assertEquals(100.0, parser.parsePrice("&a100", "$"), 1e-9);
	}

	@Test
	@DisplayName("Observation #20 (lootchests-signs-waypoints.md): parsePrice applies no sign check, "
	             + "so a negative price parses cleanly")
	void parsePrice_acceptsNegative_pinsObservation20() throws SignValidationException {
		assertEquals(-50.0, parser.parsePrice("-50", "$"), 1e-9);
	}

	@Test
	@DisplayName("parsePrice accepts zero")
	void parsePrice_acceptsZero() throws SignValidationException {
		assertEquals(0.0, parser.parsePrice("0", "$"), 1e-9);
	}

	@Test
	@DisplayName("parsePrice on unparseable text throws SignValidationException")
	void parsePrice_invalidNumber_throws() {
		assertThrows(SignValidationException.class, () -> parser.parsePrice("not-a-number", "$"));
	}

	@Test
	@DisplayName("parseAmount strips thousands separators")
	void parseAmount_stripsCommas() throws SignValidationException {
		assertEquals(1234, parser.parseAmount("1,234"));
	}

	@Test
	@DisplayName("Observation #20 (lootchests-signs-waypoints.md): parseAmount applies no positivity "
	             + "check either, so a negative or zero amount parses cleanly (only the validator, not "
	             + "the parser, enforces amount > 0)")
	void parseAmount_acceptsNegativeAndZero_pinsObservation20() throws SignValidationException {
		assertEquals(-3, parser.parseAmount("-3"));
		assertEquals(0, parser.parseAmount("0"));
	}

	@Test
	@DisplayName("parseAmount on unparseable text throws SignValidationException")
	void parseAmount_invalidNumber_throws() {
		assertThrows(SignValidationException.class, () -> parser.parseAmount("abc"));
	}

	@Test
	@DisplayName("parseContent strips color codes and trims whitespace")
	void parseContent_stripsColorAndTrims() {
		assertEquals("stone", parser.parseContent("  &7stone  "));
	}

	private static final class TestSignParser extends AbstractSignParser {

		TestSignParser(SignType signType) {
			super(signType);
		}

		@Override
		public ParsedSign parse(String[] lines, Location location) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

	}

}
