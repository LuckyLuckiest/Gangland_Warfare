package org.luckyraven.gangland.command.sub;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.SettingsFixture;

import java.nio.file.Path;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("T-826: /glw balance is open, /glw balance <player> stays behind an admin node")
class BalanceAdminGateTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void messages() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider().withString("Errors.Permissions.Command", "no perm"));
	}

	@Test
	void adminNodeName() {
		assertEquals("gangland.command.balance.admin", BalanceCommand.ADMIN_PERMISSION);
	}

	@Test
	@DisplayName("a sender without the admin node cannot look up another player and is told so")
	void nonAdmin_denied() {
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission(BalanceCommand.ADMIN_PERMISSION)).thenReturn(false);

		assertFalse(BalanceCommand.mayLookUpOthers(sender));
		verify(sender).sendMessage(anyString());
	}

	@Test
	@DisplayName("an admin may look up another player")
	void admin_allowed() {
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission(BalanceCommand.ADMIN_PERMISSION)).thenReturn(true);

		assertTrue(BalanceCommand.mayLookUpOthers(sender));
		verify(sender, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("B1: the tab completer stays silent for a non-admin (it runs on every keystroke)")
	void completer_nonAdmin_sendsNothing() {
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission(BalanceCommand.ADMIN_PERMISSION)).thenReturn(false);

		assertTrue(BalanceCommand.suggestions(sender, null, null).isEmpty());
		verify(sender, never()).sendMessage(anyString());
	}
}
