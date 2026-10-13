package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** GP-43: {@code /glw gang members [page]} groups the roster by rank, owner rank first, and pages it. */
@DisplayName("GangMembersCommand - members grouped by rank, paged")
class GangMembersCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private UserManager<Player>    userManager;
	private GangManager            gangManager;
	private Player                 player;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws IOException {
		GangSettings.bind(new FakeGangSettingsContract());
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(Settings::getLanguagePicked).thenReturn("en");

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

		Command.setInformationManager(mock(InformationManager.class));
		messages = GangTestMessages.shipped(tempDir);

		gangManager = mock(GangManager.class);
		userManager = mock(UserManager.class);
		player      = mock(Player.class);
		when(player.hasPermission(anyString())).thenReturn(true);
		when(player.spigot()).thenReturn(mock(Player.Spigot.class));

		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(5);
		when(userManager.getUser(player)).thenReturn(user);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("the owner rank comes first, each rank gets a header, and the page argument shows the next slice")
	void groupedByRank_paged() {
		// 1 owner + 13 soldiers (listed first in the roster on purpose) = 14 members, 10 per page
		List<Member> roster = new ArrayList<>();
		for (int i = 0; i < 13; i++) {
			roster.add(member("Soldier" + i, "soldier"));
		}
		roster.add(member("Boss", "owner"));
		Gang gang = mock(Gang.class);
		when(gang.getDisplayNameString()).thenReturn("Rats");
		when(gang.getMembers()).thenReturn(roster);
		when(gangManager.getGang(5)).thenReturn(gang);

		members().executeArgument(player, new String[]{"gang", "members"});
		List<String> first = lines();
		int boss    = indexOf(first, "Boss");
		int soldier = indexOf(first, "Soldier0");
		assertTrue(boss >= 0 && soldier > boss, "owner rank before soldiers: " + first);
		assertTrue(first.stream().anyMatch(line -> line.contains("owner")), "rank header: " + first);
		assertTrue(first.stream().noneMatch(line -> line.contains("Soldier12")), "page 1 stops at 10 members: " + first);

		clearInvocations(player);
		members().getNode().getChildren().get(0).getData()
				.executeArgument(player, new String[]{"gang", "members", "2"});
		List<String> second = lines();
		assertTrue(second.stream().anyMatch(line -> line.contains("Soldier12")), second.toString());
		assertTrue(second.stream().noneMatch(line -> line.contains("Boss")), second.toString());
	}

	private int indexOf(List<String> lines, String text) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).contains(text)) return i;
		}
		return -1;
	}

	private Argument members() {
		return new GangMembersCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                              gangManager, messages, new GangStatsService());
	}

	private List<String> lines() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(player, atLeast(0)).sendMessage(captor.capture());
		return captor.getAllValues().stream().map(ChatColor::stripColor).toList();
	}

	private Member member(String name, String rankName) {
		UUID id = UUID.randomUUID();
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.getName()).thenReturn(name);
		bukkit.when(() -> Bukkit.getOfflinePlayer(id)).thenReturn(offline);

		Rank rank = mock(Rank.class);
		when(rank.getName()).thenReturn(rankName);
		Member member = mock(Member.class);
		when(member.getUuid()).thenReturn(id);
		when(member.getRank()).thenReturn(rank);
		return member;
	}

}
