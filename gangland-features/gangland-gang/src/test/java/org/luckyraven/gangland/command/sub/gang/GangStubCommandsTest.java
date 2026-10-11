package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.sub.gang.ally.GangAllyCommand;
import org.luckyraven.gangland.command.extension.CommandContributions;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * One trivial test per stub verb of the gang parity kickoff: each exists under its label with its child shape, so the
 * lanes that fill it start from a compiling, correctly-wired argument.
 */
@DisplayName("Gang parity stubs - label and child shape")
class GangStubCommandsTest {

	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private final JavaPlugin     plugin = mock(JavaPlugin.class);
	private final Tree<Argument> tree   = new Tree<>();
	private final Argument       parent = mock(Argument.class);

	private static List<String> childLabels(Argument argument) {
		return argument.getNode().getChildren().stream()
				.map(node -> Arrays.asList(node.getData().getArguments()))
				.flatMap(List::stream)
				.toList();
	}

	@Test
	@DisplayName("upgrade has a confirm child")
	void upgrade() {
		Argument upgrade = new GangUpgradeCommand(plugin, tree, parent, new GangLimits(), new GangLevelTable(),
		                                          mock(GangManager.class));

		assertEquals(List.of("upgrade"), List.of(upgrade.getArguments()));
		assertEquals(List.of("confirm"), childLabels(upgrade));
	}

	@Test
	@DisplayName("safehouse has set, remove and list besides the optional name")
	void safehouse() {
		Argument safehouse = new GangSafehouseCommand(plugin, tree, parent, new GangSafehouseService(),
		                                              new GangLimits(), mock(GangWorldPolicy.class));

		assertEquals(List.of("safehouse"), List.of(safehouse.getArguments()));
		List<String> children = childLabels(safehouse);
		assertTrue(children.containsAll(List.of("set", "remove", "list")), children.toString());
	}

	@Test
	@DisplayName("rally has a join child")
	void rally() {
		Argument rally = new GangRallyCommand(plugin, tree, parent, new GangSafehouseService());

		assertEquals(List.of("rally"), List.of(rally.getArguments()));
		assertTrue(childLabels(rally).contains("join"));
	}

	@Test
	@DisplayName("pvp takes one on|off position")
	void pvp() {
		Argument pvp = new GangPvpCommand(plugin, tree, parent, mock(GangManager.class),
		                                  mock(FriendlyFirePolicy.class));

		assertEquals(List.of("pvp"), List.of(pvp.getArguments()));
		assertEquals(1, pvp.getNode().getChildren().size());
	}

	@Test
	@DisplayName("ally pvp is registered under ally")
	void allyPvp() {
		Argument ally = new GangAllyCommand(plugin, tree, parent, mock(UserManager.class), mock(MemberManager.class),
		                                    mock(GangManager.class), CommandContributions.none(),
		                                    mock(FriendlyFirePolicy.class));

		assertTrue(childLabels(ally).contains("pvp"), childLabels(ally).toString());
		assertTrue(childLabels(ally).contains("abandon"));
	}

	@Test
	@DisplayName("notice takes one greedy text position")
	void notice() {
		Argument notice = new GangNoticeCommand(plugin, tree, parent, null, null, null, null);

		assertEquals(List.of("notice"), List.of(notice.getArguments()));
		assertEquals(1, notice.getNode().getChildren().size());
	}

	@Test
	@DisplayName("ranking chains stat then page")
	void ranking() {
		Argument ranking = new GangRankingCommand(plugin, tree, parent, new GangStatsService());

		assertEquals(List.of("ranking"), List.of(ranking.getArguments()));
		Argument stat = ranking.getNode().getChildren().get(0).getData();
		assertEquals(1, stat.getNode().getChildren().size());
	}

	@Test
	@DisplayName("profile takes one player position")
	void profile() {
		Argument profile = new GangProfileCommand(plugin, tree, parent, new GangStatsService());

		assertEquals(List.of("profile"), List.of(profile.getArguments()));
		assertEquals(1, profile.getNode().getChildren().size());
	}

	@Test
	@DisplayName("chat has the allies child")
	void chatAllies() {
		Argument chat = new GangChatCommand(plugin, tree, parent, mock(UserManager.class), mock(MemberManager.class),
		                                    mock(GangManager.class), mock(GangMessages.class),
		                                    new GangChatToggles());

		assertTrue(childLabels(chat).contains("allies"), childLabels(chat).toString());
	}
}
