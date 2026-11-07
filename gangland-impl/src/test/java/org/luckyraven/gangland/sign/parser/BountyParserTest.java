package org.luckyraven.gangland.sign.parser;

import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.BountyParsedSign;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.validation.SignValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * A {@code [BOUNTY]} sign carries only its operation on line 2: the content is the cleaned second line, the price and
 * the amount are always zero whatever lines 3 and 4 say, and the sign type, location and raw lines pass through.
 */
@DisplayName("BountyParser - what a parsed bounty sign holds")
class BountyParserTest {

	private final SignType signType = new SignType("[Bounty]", "glw-bounty");
	private final BountyParser parser = new BountyParser(signType);

	@Test
	@DisplayName("the content is the colour-stripped, trimmed line 2; price and amount are zero")
	void parse_holdsTheCleanedContent_andZeroPriceAndAmount() throws SignValidationException {
		String[] lines    = {"[Bounty]", "  &aPay ", "$100", "5"};
		Location location = mock(Location.class);

		ParsedSign parsed = parser.parse(lines, location);

		assertInstanceOf(BountyParsedSign.class, parsed);
		assertEquals("Pay", parsed.getContent());
		assertEquals(0D, parsed.getPrice(), 0D);
		assertEquals(0, parsed.getAmount());
	}

	@Test
	@DisplayName("the sign type, location and raw lines are passed through as the same objects")
	void parse_passesTheContextThrough() throws SignValidationException {
		String[] lines    = {"[Bounty]", "view", "", ""};
		Location location = mock(Location.class);

		ParsedSign parsed = parser.parse(lines, location);

		assertSame(signType, parsed.getSignType());
		assertSame(location, parsed.getLocation());
		assertSame(lines, parsed.getRawLines());
	}
}
