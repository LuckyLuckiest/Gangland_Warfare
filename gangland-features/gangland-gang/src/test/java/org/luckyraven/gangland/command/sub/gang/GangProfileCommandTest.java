package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GangProfileCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	void unknownName_neverUsesByNameLookup() throws Exception {
		GangManager gangs = mock(GangManager.class);
		when(gangs.getAll()).thenReturn(List.of());
		Player sender = mock(Player.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
		     MockedStatic<org.luckyraven.gangland.file.configuration.Settings> settings = mockStatic(
				     org.luckyraven.gangland.file.configuration.Settings.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
			settings.when(org.luckyraven.gangland.file.configuration.Settings::getMoneySymbol).thenReturn("$");
			settings.when(org.luckyraven.gangland.file.configuration.Settings::getLanguagePicked).thenReturn("en");
			org.bukkit.Server server = mock(org.bukkit.Server.class);
			org.bukkit.plugin.PluginManager pm = mock(org.bukkit.plugin.PluginManager.class);
			when(server.getPluginManager()).thenReturn(pm);
			bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
			bukkit.when(Bukkit::getServer).thenReturn(server);
			org.luckyraven.gangland.file.configuration.Messages.init(new org.luckyraven.keystone.message.MessageProvider() {
				public String getString(String path) {
					return path;
				}

				public List<String> getStringList(String path) {
					return List.of();
				}
			});
			Argument profile = new GangProfileCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
			                                          new GangStatsService(), mock(MemberManager.class), gangs,
			                                          GangTestMessages.shipped(tempDir));
			Argument player = profile.getNode().getChildren().get(0).getData();
			player.executeArgument(sender, new String[]{"gang", "profile", "Nobody"});

			bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
			verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("Nobody"));
		}
	}
}
