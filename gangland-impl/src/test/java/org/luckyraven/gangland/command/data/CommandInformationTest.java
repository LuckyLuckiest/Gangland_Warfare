package org.luckyraven.gangland.command.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic pin for {@link CommandInformation#toString()}: joins usage and description with {@code " - "}, with
 * no other formatting (colouring/bracket-stripping happens later, in {@code GanglandChatUtil.commandDesign}).
 */
@DisplayName("CommandInformation")
class CommandInformationTest {

	@Test
	@DisplayName("toString joins usage and description with a single ' - ' separator")
	void toString_joinsUsageAndDescription() {
		CommandInformation info = new CommandInformation("/glw help [page]", "Shows the help menu");

		assertEquals("/glw help [page] - Shows the help menu", info.toString());
	}

	@Test
	@DisplayName("an empty description still produces the trailing separator")
	void toString_emptyDescription_stillHasSeparator() {
		CommandInformation info = new CommandInformation("/glw ping", "");

		assertEquals("/glw ping - ", info.toString());
	}
}
