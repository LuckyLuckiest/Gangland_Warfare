package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
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
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.permission.PermissionManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** GP-04: {@code /glw gang help} lists only the verbs the sender may run. */
@DisplayName("GangCommand help - only entries the sender may run")
class GangHelpFilterTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;

	@BeforeEach
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
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("GP-04: a verb the sender lacks the node for, and an entry whose verb has no node, are not listed")
	@SuppressWarnings("unchecked")
	void entryWithoutNode_notListed() {
		GangCommand command = new GangCommand(mock(JavaPlugin.class), mock(UserManager.class), mock(UserManager.class),
		                                      null, null, null, null, mock(PermissionManager.class), mock(DependencyContainer.class), null,
		                                      mock(GangRules.class), messages, new GangChatToggles(),
		                                      new GangWorldPolicy(mock(GangRules.class)), mock(GangMembershipService.class),
		                                      mock(GangDisbandService.class), mock(GangLeadership.class), new CombatTag(),
		                                      new GangStatsService(), new GangLimits(), new GangLevelTable(),
		                                      mock(GangSafehouseService.class), mock(FriendlyFirePolicy.class));

		command.initializeArguments();

		String balanceNode = command.getArgument().getNode().getChildren().stream()
				.map(node -> node.getData())
				.filter(child -> child.getArguments()[0].equals("balance"))
				.map(Argument::getPermission)
				.findFirst().orElseThrow();

		command.getHelpInfo().clear();
		command.getHelpInfo().add(new CommandInformation("/glw gang info <gang|player>", "DESC_INFO"));
		command.getHelpInfo().add(new CommandInformation("/glw gang balance", "DESC_BALANCE"));
		command.getHelpInfo().add(new CommandInformation("/glw gang nosuchverb", "DESC_NOSUCH"));

		Player player = mock(Player.class);
		when(player.hasPermission(anyString())).thenAnswer(call -> !balanceNode.equals(call.getArgument(0)));

		command.help(player, 1);

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(player, atLeast(0)).sendMessage(lines.capture());
		String shown = String.join("\n", lines.getAllValues().stream().map(ChatColor::stripColor).toList());
		assertTrue(shown.contains("DESC_INFO"), shown);
		assertTrue(!shown.contains("DESC_BALANCE"), "balance is gated: " + shown);
		assertTrue(!shown.contains("DESC_NOSUCH"), "no node: " + shown);
	}

}
