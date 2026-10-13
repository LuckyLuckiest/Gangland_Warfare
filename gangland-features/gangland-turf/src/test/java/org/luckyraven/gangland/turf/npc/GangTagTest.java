package org.luckyraven.gangland.turf.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GangTagTest {

	@Test
	void examples() {
		assertEquals("VIP", GangTag.of("Vipers", 1));
		assertEquals("RED", GangTag.of("&cRed Dragons", 2));
		assertEquals("LSV", GangTag.of("Los Santos Vagos", 3));
		assertEquals("TBBW", GangTag.of("The Big Bad Wolves Club", 4));
		assertEquals("#42", GangTag.of("!!!", 42));
		assertEquals("#123", GangTag.of(null, 12345));
		assertEquals("AL", GangTag.of("Al", 5));
		assertTrue(GangTag.of("Abcdefghijklmnopqrstuvwxyzabcdefghijklmn", 6).length() <= 4);
		assertFalse(GangTag.of("a-b-c-d-e", 7).contains("-"));
	}
}
