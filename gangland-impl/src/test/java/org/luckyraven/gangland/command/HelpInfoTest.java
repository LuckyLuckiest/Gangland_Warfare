package org.luckyraven.gangland.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * {@link HelpInfo} paging maths and rendering: {@code getMaxPages()} ceiling division, the empty-list branch, the
 * {@code page < 1} / {@code page > maxPages} exceptions (Errors.Help messages are raw English per
 * commands-messages-platform.md Observation #19 — this class pins the exact exception text, not a
 * {@code Messages} key, since none is used), and the exact slice boundary at the default {@code breaks = 7}.
 *
 * <p>{@code displayHelp} routes every line through {@code GanglandChatUtil.color}/{@code commandDesign}, so this
 * class initializes {@code Settings} (money symbol) and {@code Messages} once in {@code @BeforeAll}.
 */
@DisplayName("HelpInfo")
class HelpInfoTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void initStatics() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				.withString("Errors.Prefix", "[E] ")
				.withString("Errors.Help.No_Entries", "nothing to show"));
	}

	private static CommandInformation info(int n) {
		return new CommandInformation("/glw cmd" + n, "desc" + n);
	}

	@Test
	@DisplayName("getMaxPages is a ceiling division of entry count by the page size")
	void getMaxPages_ceilingDivision() {
		assertEquals(0, new HelpInfo().getMaxPages());

		HelpInfo one = new HelpInfo();
		one.add(info(1));
		assertEquals(1, one.getMaxPages());

		HelpInfo exactMultiple = new HelpInfo(7);
		for (int i = 0; i < 14; i++) exactMultiple.add(info(i));
		assertEquals(2, exactMultiple.getMaxPages());

		HelpInfo remainder = new HelpInfo(3);
		for (int i = 0; i < 7; i++) remainder.add(info(i));
		assertEquals(3, remainder.getMaxPages(), "ceil(7 / 3) == 3");
	}

	@Test
	@DisplayName("displayHelp on an empty list sends only the localized empty-help message")
	void displayHelp_emptyList_sendsLocalizedEmptyMessage() {
		CommandSender sender = mock(CommandSender.class);

		new HelpInfo().displayHelp(sender, 1, "Title");

		verify(sender).sendMessage("[E] nothing to show");
		verifyNoMoreInteractions(sender);
	}

	@Test
	@DisplayName("page below 1 throws the raw 'Cannot get page less than 1' message")
	void displayHelp_pageBelowOne_throws() {
		HelpInfo help = new HelpInfo();
		help.add(info(1));

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> help.displayHelp(mock(CommandSender.class), 0, "Title"));

		assertEquals("Cannot get page less than 1", exception.getMessage());
	}

	@Test
	@DisplayName("page beyond maxPages throws the raw 'Cannot exceed maximum allowed pages' message")
	void displayHelp_pageAboveMax_throws() {
		HelpInfo help = new HelpInfo();
		help.add(info(1));

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> help.displayHelp(mock(CommandSender.class), 2, "Title"));

		assertEquals("Cannot exceed maximum allowed pages", exception.getMessage());
	}

	@Test
	@DisplayName("a full first page sends 3 header lines plus exactly `breaks` command lines")
	void displayHelp_fullPage_sendsHeaderPlusBreaksLines() {
		HelpInfo help = new HelpInfo(7);
		for (int i = 0; i < 9; i++) help.add(info(i));
		CommandSender sender = mock(CommandSender.class);

		help.displayHelp(sender, 1, "Title");

		verify(sender, times(10)).sendMessage(anyString()); // "", header, "" + 7 command lines
	}

	@Test
	@DisplayName("the last (partial) page sends only the remaining entries")
	void displayHelp_lastPartialPage_sendsOnlyRemainder() {
		HelpInfo help = new HelpInfo(7);
		for (int i = 0; i < 9; i++) help.add(info(i));
		CommandSender sender = mock(CommandSender.class);

		help.displayHelp(sender, 2, "Title");

		verify(sender, times(5)).sendMessage(anyString()); // "", header, "" + 2 remaining command lines
	}
}
