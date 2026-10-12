package org.luckyraven.gangland.command.sub.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GangNoticeCommand.update")
class GangNoticeCommandTest {

	@Test
	@DisplayName("text is stored, 'clear' removes it, an over-long text is refused")
	void update() {
		Gang gang = new Gang(1, new ArrayList<>(), "Rats");

		assertTrue(GangNoticeCommand.update(gang, "Meet at the docks"));
		assertEquals("Meet at the docks", gang.getNotice());

		assertFalse(GangNoticeCommand.update(gang, "x".repeat(300)));
		assertEquals("Meet at the docks", gang.getNotice());

		assertTrue(GangNoticeCommand.update(gang, "clear"));
		assertNull(gang.getNotice());
	}

	@Test
	@DisplayName("a notice that fits 200 visible characters but overflows the 255-char column is refused")
	void rawLengthOverflowRefused() {
		Gang gang = new Gang(1, new ArrayList<>(), "Rats");
		assertTrue(GangNoticeCommand.update(gang, "Meet at the docks"));

		// 150 visible characters, each carrying an "&a" code: 450 raw characters, past the VARCHAR(255) column
		String coloured = "&ax".repeat(150);
		assertFalse(GangNoticeCommand.update(gang, coloured));
		assertEquals("Meet at the docks", gang.getNotice());
	}

}
