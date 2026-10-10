package org.luckyraven.gangland.command.sub.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.gangland.gang.Gang;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

}
