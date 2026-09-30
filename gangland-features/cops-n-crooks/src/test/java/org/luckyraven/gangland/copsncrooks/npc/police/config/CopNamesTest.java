package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CopNames - callsigns and the short Citizens name (phase H13)")
class CopNamesTest {

	@Test
	@DisplayName("the callsign fills %rank%, %name% and %badge%, colours kept")
	void callsign_fillsPlaceholders() {
		assertEquals("&9Officer &fBob &7#1592", CopNames.DEFAULT.callsign("&9Officer", "Bob", 1592));
	}

	@Test
	@DisplayName("an empty first name collapses to a single space")
	void callsign_emptyName_singleSpace() {
		assertEquals("&9Officer &7#1592", CopNames.DEFAULT.callsign("&9Officer", "", 1592));
	}

	@Test
	@DisplayName("the short name is the plain first name and badge")
	void shortName_nameAndBadge() {
		assertEquals("Bob #1592", CopNames.shortName("Bob", 1592));
		assertEquals("#1592", CopNames.shortName("", 1592));
	}

	@Test
	@DisplayName("the short name never exceeds 16 characters and carries no colour codes")
	void shortName_atMost16_plain() {
		String name = CopNames.shortName("&cBartholomew-Alexander", 123456);
		assertTrue(name.length() <= 16, name);
		assertTrue(name.endsWith(" #123456"), name);
		assertFalse(name.contains("&") || name.contains("§"), name);
	}

	@Test
	@DisplayName("the first name comes from the pool; an empty pool gives an empty name")
	void pickName_fromPool() {
		Random random = new Random(1);
		assertEquals("Bob", new CopNames("%name%", List.of("Bob")).pickName(random));
		assertEquals("", new CopNames("%name%", List.of()).pickName(random));
		assertFalse(CopNames.DEFAULT.firstNames().isEmpty());
	}

	@Test
	@DisplayName("the badge is 1000 plus the Citizens id")
	void badge_isIdPlus1000() {
		assertEquals(1017, CopNames.badge(17));
	}
}
