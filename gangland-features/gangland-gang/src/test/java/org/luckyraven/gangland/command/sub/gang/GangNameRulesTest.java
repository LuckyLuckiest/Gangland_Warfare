package org.luckyraven.gangland.command.sub.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.file.GangRules;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link GangNameRules}: the pattern, colour stripping, and uniqueness that ignores the renamed gang itself. */
@DisplayName("GangNameRules - create and rename name checks")
class GangNameRulesTest {

	private static final Pattern DEFAULT = Pattern.compile("[A-Za-z0-9_]{3,16}");

	@ParameterizedTest(name = "[{index}] \"{0}\" with {1} -> {2}")
	@CsvSource(delimiter = '|', value = {
			"Rats                 | [A-Za-z0-9_]{3,16} | ok",
			"ab                   | [A-Za-z0-9_]{3,16} | Names.Invalid",
			"abcdefghijklmnopq    | [A-Za-z0-9_]{3,16} | Names.Invalid",
			"Rat s                | [A-Za-z0-9_]{3,16} | Names.Invalid",
			"&cRats               | [A-Za-z0-9_]{3,16} | ok",
			"Крысы                | [A-Za-z0-9_]{3,16} | Names.Invalid",
			"Крысы                | \\p{L}{3,16}       | ok"
	})
	@DisplayName("the stripped name must match the pattern; an empty table means accepted")
	void matchesPattern(String raw, String regex, String expected) {
		Optional<String> refusal = GangNameRules.check(raw, Pattern.compile(regex.trim()), List.of(), null);

		assertEquals("ok".equals(expected) ? Optional.empty() : Optional.of(expected), refusal);
	}

	@Test
	@DisplayName("a name equal to another gang's, ignoring case, is taken")
	void duplicate_ignoringCase_isTaken() {
		Collection<Gang> existing = List.of(gang(3, "Rats"));

		assertEquals(Optional.of("Names.Taken"), GangNameRules.check("rats", DEFAULT, existing, null));
	}

	@Test
	@DisplayName("a duplicate is refused after colour codes are stripped")
	void duplicate_colourStripped_isTaken() {
		Collection<Gang> existing = List.of(gang(3, "Rats"));

		assertEquals(Optional.of("Names.Taken"), GangNameRules.check("&cRats", DEFAULT, existing, null));
	}

	@Test
	@DisplayName("renaming a gang to its own name is accepted")
	void renameToOwnName_accepted() {
		Collection<Gang> existing = List.of(gang(5, "Rats"));

		assertEquals(Optional.empty(), GangNameRules.check("Rats", DEFAULT, existing, 5));
	}

	private static Gang gang(int id, String name) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		when(gang.getName()).thenReturn(name);
		return gang;
	}

	// ---- display names (W5-U2: GP-06) ----

	private static GangRules rules(boolean hex, boolean colours, int max, String... blocked) {
		GangRules rules = mock(GangRules.class);
		when(rules.isDisplayNameAllowHex()).thenReturn(hex);
		when(rules.isDisplayNameParseColours()).thenReturn(colours);
		when(rules.getDisplayNameMaxLength()).thenReturn(max);
		when(rules.getDisplayNameBlockedWords()).thenReturn(List.of(blocked));
		return rules;
	}

	@Test
	@DisplayName("a Cyrillic display name with a hex colour is accepted")
	void cyrillicDisplay_ok() {
		assertEquals(Optional.empty(),
		             GangNameRules.checkDisplay("&#ff8800\u0417\u043c\u0435\u0438", rules(true, true, 24), List.of(), null));
	}

	@Test
	@DisplayName("hex colours are refused when Allow_Hex is off, accepted when on; length counts visible letters")
	void hexAllowedAndDenied() {
		assertEquals(Optional.of("Names.Display_Hex"),
		             GangNameRules.checkDisplay("&#ff8800Snakes", rules(false, true, 24), List.of(), null));
		assertEquals(Optional.empty(),
		             GangNameRules.checkDisplay("&#ff8800Snakes", rules(true, true, 24), List.of(), null));
		assertEquals(Optional.empty(),
		             GangNameRules.checkDisplay("&#ff8800Snakes", rules(true, true, 6), List.of(), null));
		assertEquals(Optional.of("Names.Display_Too_Long"),
		             GangNameRules.checkDisplay("&#ff8800Snakes", rules(true, true, 5), List.of(), null));
	}

	@Test
	@DisplayName("a name pattern that accepts whitespace, @, / or ; is flagged; sane patterns are not")
	void whitespaceOrAtPattern_rejectedAtLoad() {
		assertTrue(GangNameRules.allowsForbidden(Pattern.compile("[A-Za-z0-9_ ]{3,16}")));
		assertTrue(GangNameRules.allowsForbidden(Pattern.compile("[\\w@]{3,16}")));
		assertTrue(GangNameRules.allowsForbidden(Pattern.compile(".{3,16}")));
		assertTrue(GangNameRules.allowsForbidden(Pattern.compile("\\S{3,16}")));
		assertFalse(GangNameRules.allowsForbidden(DEFAULT));
		assertFalse(GangNameRules.allowsForbidden(Pattern.compile("\\p{L}{3,16}")));
	}

	@Test
	@DisplayName("a display equal to another gang's name or display, ignoring colour and case, is taken")
	void colourStrippedDuplicate_refused() {
		Gang rats   = gang(3, "Rats");
		Gang snakes = gang(4, "Snakes");
		when(snakes.getDisplayName()).thenReturn("&#ff8800SNAKES");
		GangRules rules = rules(true, true, 24);

		assertEquals(Optional.of("Names.Display_Taken"),
		             GangNameRules.checkDisplay("&cRats", rules, List.of(rats), null));
		assertEquals(Optional.of("Names.Display_Taken"),
		             GangNameRules.checkDisplay("&asnakes", rules, List.of(rats, snakes), null));
		assertEquals(Optional.empty(), GangNameRules.checkDisplay("&cRats", rules, List.of(rats), 3));
	}

	@Test
	@DisplayName("look-alike letters from another script do not make a name unique")
	void homoglyphTwin_refused() {
		Collection<Gang> existing = List.of(gang(3, "Rats"));

		// \u0430 is the Cyrillic a
		assertEquals(Optional.of("Names.Display_Taken"),
		             GangNameRules.checkDisplay("R\u0430ts", rules(true, true, 24), existing, null));
		assertEquals(Optional.of("Names.Taken"),
		             GangNameRules.check("R\u0430ts", Pattern.compile("\\p{L}{3,16}"), existing, null));
		assertEquals(GangNameRules.key("Rats"), GangNameRules.key("&cR\u0430TS"));
	}

	@Test
	@DisplayName("a blocked word is refused inside a display name, ignoring colour, case and look-alikes")
	void blockedWord_refused() {
		GangRules rules = rules(true, true, 24, "badword");

		assertEquals(Optional.of("Names.Display_Blocked"),
		             GangNameRules.checkDisplay("My&cBADWORD", rules, List.of(), null));
		assertEquals(Optional.of("Names.Display_Blocked"),
		             GangNameRules.checkDisplay("b\u0430dword", rules, List.of(), null));
		assertEquals(Optional.empty(), GangNameRules.checkDisplay("Goodgang", rules, List.of(), null));
	}

	@Test
	@DisplayName("Parse_Colours off stores the display without colour codes")
	void parseColoursOff_stripsCodes() {
		assertEquals("Snakes", GangNameRules.storedDisplay("&#ff8800&cSnakes", rules(true, false, 24)));
		assertEquals("&#ff8800Snakes", GangNameRules.storedDisplay("&#ff8800Snakes", rules(true, true, 24)));
	}

}
