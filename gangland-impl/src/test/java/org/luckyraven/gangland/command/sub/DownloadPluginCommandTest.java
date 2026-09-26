package org.luckyraven.gangland.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.update.UpdateNotifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * gi=88: the bare {@code /glw update} status line used to decide "is there an update" with raw
 * {@code !getLatestVersion().equalsIgnoreCase(currentVersion)} instead of {@link UpdateNotifier#updateAvailable()}
 * (the same semantic-version compare {@code /glw update download} already uses) — any fetched version string that
 * merely differed from the running one, including a textually-older one, read as "new update available".
 */
@DisplayName("DownloadPluginCommand")
class DownloadPluginCommandTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install(); // Command's Argument tree registers a Bukkit permission on construction
		Messages.init(new FakeMessageProvider()
				              .withString("Commands.Update.Available", "update available")
				              .withString("Commands.Update.Latest", "already latest"));
		Command.setInformationManager(new InformationManager());
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a textually-different but semantically-older fetched version reports already latest, not available")
	void onExecute_matchesUpdateAvailableSemantics_notRawStringInequality() {
		PluginDescriptionFile description = mock(PluginDescriptionFile.class);
		when(description.getVersion()).thenReturn("0.10.0");

		Gangland gangland = mock(Gangland.class);
		when(gangland.getDescription()).thenReturn(description);

		UpdateNotifier updateChecker = mock(UpdateNotifier.class);
		// Textually different from "0.10.0", but the real semantic check says no update - exactly what
		// getConfirm() already relies on via updateAvailable().
		when(updateChecker.getLatestVersion()).thenReturn("0.7.5");
		when(updateChecker.updateAvailable()).thenReturn(false);
		when(gangland.getUpdateChecker()).thenReturn(updateChecker);

		DownloadPluginCommand command = new DownloadPluginCommand(gangland);
		CommandSender          sender  = mock(CommandSender.class);

		command.onExecute(mock(Argument.class), sender, new String[0]);

		verify(sender).sendMessage(Messages.UPDATE_LATEST.toString());
	}

	@Test
	@DisplayName("a real newer version still reports available")
	void onExecute_realNewerVersion_reportsAvailable() {
		PluginDescriptionFile description = mock(PluginDescriptionFile.class);
		when(description.getVersion()).thenReturn("0.10.0");

		Gangland gangland = mock(Gangland.class);
		when(gangland.getDescription()).thenReturn(description);

		UpdateNotifier updateChecker = mock(UpdateNotifier.class);
		when(updateChecker.getLatestVersion()).thenReturn("0.11.0");
		when(updateChecker.updateAvailable()).thenReturn(true);
		when(gangland.getUpdateChecker()).thenReturn(updateChecker);

		DownloadPluginCommand command = new DownloadPluginCommand(gangland);
		CommandSender          sender  = mock(CommandSender.class);

		command.onExecute(mock(Argument.class), sender, new String[0]);

		verify(sender).sendMessage(Messages.UPDATE_AVAILABLE.toString().replace("%short_prefix%", "glw"));
	}
}
