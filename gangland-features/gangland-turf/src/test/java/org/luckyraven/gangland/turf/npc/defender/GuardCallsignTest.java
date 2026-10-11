package org.luckyraven.gangland.turf.npc.defender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GuardCallsign")
class GuardCallsignTest {

	@Test
	@DisplayName("tag_threeToFourLetters: the first three letters of the display name, upper case")
	void tag_threeToFourLetters() {
		assertEquals("VIP", GuardCallsign.tag("Vipers", "vipers"));
		String tag = GuardCallsign.tag("Ox", "Oxen");
		assertTrue(tag.length() >= 3 && tag.length() <= 4, tag);
		assertEquals("OXO", tag, "a short name is padded from the gang name");
	}

	@Test
	@DisplayName("colourAndSymbolsStripped: colour codes, spaces and symbols never reach the tag")
	void colourAndSymbolsStripped() {
		assertEquals("RED", GuardCallsign.tag("&cRed Dragons", "reddragons"));
		assertEquals("RED", GuardCallsign.tag("\u00a7c\u00a7lRed Dragons", "x"));
		assertEquals("A1B", GuardCallsign.tag("[A!1] B", "x"));
	}

	@Test
	@DisplayName("a blank or symbol-only name falls back to the gang name, and never to an empty tag")
	void tag_neverEmpty() {
		assertEquals("RAT", GuardCallsign.tag("&c!!", "Rats"));
		assertEquals("GNG", GuardCallsign.tag("", ""));
		assertEquals("GNG", GuardCallsign.tag(null, null));
	}

	@Test
	@DisplayName("neverCit: a CIT tag grows to four letters so it cannot read as a Citizens name")
	void neverCit() {
		assertEquals("CITY", GuardCallsign.tag("City Boys", "cityboys"));
		assertFalse(GuardCallsign.tag("Cit", "cit").equals("CIT"));
		assertFalse(GuardCallsign.npcName("CIT", 1).startsWith("CIT"));
	}

	@Test
	@DisplayName("format_fitsSixteen: the callsign reads [TAG] Guard n and the Citizens name stays within 16")
	void format_fitsSixteen() {
		assertEquals("[VIP] Guard 3", GuardCallsign.format("VIP", 3));
		assertEquals(13, GuardCallsign.format("VIP", 3).length());
		assertTrue(GuardCallsign.format("VIPR", 999).length() <= 16);
		assertTrue(GuardCallsign.npcName("VIPR", 123456).length() <= 16);
		assertEquals(GuardCallsign.format("VIP", 3), GuardCallsign.npcName("VIP", 3));
	}
}
