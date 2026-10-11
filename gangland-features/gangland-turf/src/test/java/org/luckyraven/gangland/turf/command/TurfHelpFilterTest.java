package org.luckyraven.gangland.turf.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.data.CommandInformation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code /glw turf} help (item 9): a player without turf admin sees only turf, turf info and turf list; an admin sees
 * every entry. Tested through {@link TurfCommand#visibleEntries}.
 */
@DisplayName("TurfCommand help - entries filtered by the sender's admin rights")
class TurfHelpFilterTest {

	private static final List<CommandInformation> ALL = List.of(
			new CommandInformation("/glw turf", "root"),
			new CommandInformation("/glw turf wand", "wand"),
			new CommandInformation("/glw turf info [id|name]", "info"),
			new CommandInformation("/glw turf list [page]", "list"),
			new CommandInformation("/glw turf rules", "rules"),
			new CommandInformation("/glw turf quartermaster", "quartermaster"),
			new CommandInformation("/glw turf tp [id]", "tp"));

	@Test
	@DisplayName("a non-admin sees turf, turf info, turf list, turf rules and turf quartermaster only")
	void visibleEntries_nonAdmin_playerVerbsOnly() {
		List<String> usages = TurfCommand.visibleEntries(ALL, false).stream()
				.map(CommandInformation::usage)
				.toList();

		assertEquals(List.of("/glw turf", "/glw turf info [id|name]", "/glw turf list [page]", "/glw turf rules",
		                    "/glw turf quartermaster"),
		             usages);
	}

	@Test
	@DisplayName("an admin sees every entry")
	void visibleEntries_admin_all() {
		assertEquals(ALL, TurfCommand.visibleEntries(ALL, true));
	}
}
