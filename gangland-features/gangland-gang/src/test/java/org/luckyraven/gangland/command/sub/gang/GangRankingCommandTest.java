package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
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
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.gang.support.StatsFixtures.gang;
import static org.luckyraven.gangland.gang.support.StatsFixtures.member;
import static org.luckyraven.gangland.gang.support.StatsFixtures.service;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GangRankingCommand - /glw gang ranking [stat] [page]")
class GangRankingCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private GangStatsService       stats;
	private GangRules              rules;
	private Player                 player;

	@BeforeEach
	void setUp() throws IOException {
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
		settings = mockStatic(Settings.class, org.mockito.Mockito.CALLS_REAL_METHODS);
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

		stats = service(0, 10, List.of(),
		                gang(1, "Alpha", 3, 100, member(1, 20, 5, 1)),
		                gang(2, "Bravo", 5, 900, member(2, 40, 8, 9)),
		                gang(3, "Charlie", 1, 50, member(3, 11, 0, 0)));
		rules = mock(GangRules.class);
		when(rules.getRankingPageSize()).thenReturn(2);

		player = mock(Player.class);
		when(player.hasPermission(anyString())).thenReturn(true);
		when(player.spigot()).thenReturn(mock(Player.Spigot.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	private Argument ranking() {
		return new GangRankingCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), stats, messages,
		                              rules);
	}

	private static Argument stat(Argument ranking) {
		return ranking.getNode().getChildren().get(0).getData();
	}

	private static Argument page(Argument ranking) {
		return stat(ranking).getNode().getChildren().get(0).getData();
	}

	private static List<String> sent(CommandSender sender) {
		var captor = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeast(0)).sendMessage(captor.capture());
		return captor.getAllValues().stream().map(ChatColor::stripColor).toList();
	}

	private static String footerClick(CommandSender sender) {
		var captor = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(sender.spigot(), atLeast(0)).sendMessage(captor.capture());
		if (captor.getAllValues().isEmpty()) return null;
		for (BaseComponent component : captor.getValue()) {
			ClickEvent click = component.getClickEvent();
			if (click != null) return click.getValue();
		}
		return null;
	}

	@Test
	@DisplayName("no argument ranks by kills, page 1")
	void defaultIsKills() {
		ranking().executeArgument(player, new String[]{"gang", "ranking"});

		List<String> lines = sent(player);
		assertTrue(lines.get(0).contains("kills"), lines.toString());
		assertEquals("#1 Bravo - 40", lines.get(1));
		assertEquals("#2 Alpha - 20", lines.get(2));
	}

	@Test
	@DisplayName("paging: page size 2 gives a next-page button, page 2 holds the rest, an overshoot clamps")
	void paging() {
		Argument ranking = ranking();
		ranking.executeArgument(player, new String[]{"gang", "ranking"});
		assertEquals(3, sent(player).size(), "header and two rows");
		assertEquals("/glw gang ranking kills 2", footerClick(player));

		Player second = mock(Player.class);
		when(second.hasPermission(anyString())).thenReturn(true);
		when(second.spigot()).thenReturn(mock(Player.Spigot.class));
		page(ranking).executeArgument(second, new String[]{"gang", "ranking", "kills", "9"});
		List<String> lines = sent(second);
		assertTrue(lines.get(0).contains("2/2"), lines.toString());
		assertEquals(List.of("#3 Charlie - 11"), lines.subList(1, lines.size()));
		assertEquals(null, footerClick(second));
	}

	@Test
	@DisplayName("unknownStat_listsValid: the answer names the stats the asker may use, wealth only with the node")
	void unknownStat_listsValid() {
		stat(ranking()).executeArgument(player, new String[]{"gang", "ranking", "bogus"});
		String reply = sent(player).get(0);
		assertTrue(reply.contains("kills") && reply.contains("kdr") && reply.contains("turfs"), reply);
		assertTrue(reply.contains("balance"), "wealth node held: " + reply);

		Player plain = mock(Player.class);
		when(plain.hasPermission(anyString())).thenReturn(true);
		when(plain.hasPermission(GangPermissions.RANKING_WEALTH)).thenReturn(false);
		stat(ranking()).executeArgument(plain, new String[]{"gang", "ranking", "bogus"});
		String hidden = sent(plain).get(0);
		assertTrue(hidden.contains("kills") && !hidden.contains("balance") && !hidden.contains("money"), hidden);
	}

	@Test
	@DisplayName("a wealth stat without the wealth node is refused like an unknown one")
	void wealthNeedsNode() {
		Player plain = mock(Player.class);
		when(plain.hasPermission(anyString())).thenReturn(true);
		when(plain.hasPermission(GangPermissions.RANKING_WEALTH)).thenReturn(false);

		stat(ranking()).executeArgument(plain, new String[]{"gang", "ranking", "balance"});
		assertTrue(sent(plain).get(0).contains("Unknown stat"), sent(plain).toString());

		stat(ranking()).executeArgument(player, new String[]{"gang", "ranking", "balance"});
		assertEquals("#1 Bravo - 900", sent(player).get(1).replace(",", "").replace(".00", ""));
	}
}
