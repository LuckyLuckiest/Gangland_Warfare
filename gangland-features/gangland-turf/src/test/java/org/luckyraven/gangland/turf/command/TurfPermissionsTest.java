package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TurfPermissions}: one admin node (TF-30) and the single refusal line a non-admin sees.
 */
@DisplayName("TurfPermissions - one admin namespace (TF-30)")
class TurfPermissionsTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private TurfMessages           text;

	@BeforeEach
	void setUp() throws IOException {
		settings = TurfTextFixture.stubMoneySymbol();
		text     = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private static CommandSender sender(boolean admin) {
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission(WandSelectionManager.ADMIN_PERMISSION)).thenReturn(admin);
		return sender;
	}

	@Test
	@DisplayName("the admin node is gangland.turf.admin")
	void adminNode_isOneNamespace() {
		assertEquals("gangland.turf.admin", TurfPermissions.ADMIN);
	}

	@Test
	@DisplayName("requireAdmin is true and silent for a sender with the node")
	void requireAdmin_withNode_allowedSilently() {
		CommandSender sender = sender(true);

		assertTrue(TurfPermissions.requireAdmin(sender, text));
		assertTrue(TurfPermissions.isAdmin(sender));
		verify(sender, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	@DisplayName("requireAdmin is false and sends one refusal line without the node")
	void requireAdmin_withoutNode_refusedWithOneLine() {
		CommandSender sender = sender(false);

		assertFalse(TurfPermissions.requireAdmin(sender, text));
		assertFalse(TurfPermissions.isAdmin(sender));
		verify(sender).sendMessage(text.text("Turf.Admin_Only", "&cOnly turf admins can do that."));
	}
}
