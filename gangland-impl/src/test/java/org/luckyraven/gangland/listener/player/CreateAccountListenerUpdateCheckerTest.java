package org.luckyraven.gangland.listener.player;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.update.UpdateNotifier;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression net for CM-01 (Observation #1, commands-messages-platform.md) — the update-checker half of
 * {@link CreateAccountListener}. Split out from {@code CreateAccountListenerTest}, which owns the join-time
 * economy contract (documentation/TESTING.md §2: split by concern rather than growing one class).
 *
 * <p>{@code Gangland.updateCheckerInitializer()} returns before ever constructing the {@link UpdateNotifier} when
 * {@code Update_Checker.Enable} is {@code false}, so {@code gangland.getUpdateChecker()} is {@code null} on every
 * server that disabled the updater. {@code onPlayerJoin} runs at {@code EventPriority.LOWEST} and used to
 * dereference the notifier unconditionally, so the NPE aborted the handler <em>before</em>
 * {@code userManager.add(user)} — leaving the joining player with no cached {@link User} at all.
 */
@DisplayName("CreateAccountListener.notifyUpdate — CM-01 update-checker null guard")
class CreateAccountListenerUpdateCheckerTest {

	@TempDir
	static Path tempDir;

	private Player         player;
	private UpdateNotifier updateChecker;

	@BeforeAll
	static void initStatics() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider().withString("Normal.Prefix", "&7[GLW] "));
	}

	@BeforeEach
	void setUp() {
		player        = mock(Player.class);
		updateChecker = mock(UpdateNotifier.class);
	}

	@Test
	@DisplayName("CM-01: a null update checker sends nothing instead of throwing")
	void notifyUpdate_nullChecker_sendsNothing() {
		assertFalse(assertDoesNotThrow(() -> CreateAccountListener.notifyUpdate(player, null)),
		            "with Update_Checker.Enable false the notifier is never built, so a disabled updater must be a "
		            + "no-op rather than an NPE that aborts the whole LOWEST-priority join handler");

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a player without the check permission is never notified")
	void notifyUpdate_withoutPermission_sendsNothing() {
		when(updateChecker.getCheckPermission()).thenReturn("gangland.update");
		when(player.hasPermission("gangland.update")).thenReturn(false);

		assertFalse(CreateAccountListener.notifyUpdate(player, updateChecker));

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a permitted player is not notified while no update is available")
	void notifyUpdate_noUpdateAvailable_sendsNothing() {
		when(updateChecker.getCheckPermission()).thenReturn("gangland.update");
		when(player.hasPermission("gangland.update")).thenReturn(true);
		when(updateChecker.updateAvailable()).thenReturn(false);

		assertFalse(CreateAccountListener.notifyUpdate(player, updateChecker));

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a permitted player with a pending update still receives the prefixed notice")
	void notifyUpdate_permittedAndUpdateAvailable_sendsNotice() {
		when(updateChecker.getCheckPermission()).thenReturn("gangland.update");
		when(player.hasPermission("gangland.update")).thenReturn(true);
		when(updateChecker.updateAvailable()).thenReturn(true);
		when(updateChecker.getUpdateMessage()).thenReturn("a new version is out");

		assertTrue(CreateAccountListener.notifyUpdate(player, updateChecker));

		verify(player).sendMessage(anyString());
	}
}
