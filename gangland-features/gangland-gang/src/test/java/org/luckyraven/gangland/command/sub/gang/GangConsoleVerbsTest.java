package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
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
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T-227: the console reaches {@code /glw gang list} and {@code /glw gang info <gang>}; every other verb is refused once,
 * centrally. GR-40: the root gate answers one line in a world where gangs are off, and runs nothing.
 */
@DisplayName("GangCommand - console verbs (T-227) and the world gate (GR-40)")
class GangConsoleVerbsTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private GangManager            gangManager;
	private GangRules              rules;
	private CommandSender          console;

	@BeforeEach
	void setUp() throws IOException {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
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
		settings.when(Settings::isGangEnabled).thenReturn(true);

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());

		Command.setInformationManager(mock(InformationManager.class));
		messages = GangTestMessages.shipped(tempDir);

		Gang rats = new Gang(7);
		rats.setName("Rats");
		gangManager = mock(GangManager.class);
		when(gangManager.getAll()).thenReturn(List.of(rats));
		when(gangManager.getGang(7)).thenReturn(rats);

		rules = mock(GangRules.class);
		when(rules.getDisabledWorlds()).thenReturn(Set.of("world_nether"));

		console = mock(CommandSender.class);
		when(console.hasPermission(anyString())).thenReturn(true);
		when(console.spigot()).thenReturn(mock(CommandSender.Spigot.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	private GangCommand command() {
		GangCommand command = new GangCommand(mock(JavaPlugin.class), mock(UserManager.class),
		                                      mock(UserManager.class), gangManager, mock(MemberManager.class),
		                                      mock(RankManager.class), mock(RepositoryRegistry.class),
		                                      mock(PermissionManager.class), mock(DependencyContainer.class), null,
		                                      rules, messages, new GangChatToggles(), new GangWorldPolicy(rules),
		                                      mock(GangMembershipService.class), mock(GangDisbandService.class),
		                                      mock(GangLeadership.class), new CombatTag(), new GangStatsService(),
		                                      new GangLimits(), new GangLevelTable(), mock(GangSafehouseService.class),
		                                      mock(FriendlyFirePolicy.class));
		command.initializeArguments();
		return command;
	}

	@Test
	@DisplayName("consoleList_runs")
	void consoleList_runs() {
		command().runExecute("glw", console, new String[]{"gang", "list"});

		ArgumentCaptor<BaseComponent[]> rows = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(console.spigot()).sendMessage(rows.capture());
		assertTrue(rows.getValue()[0].toPlainText().contains("Rats"), rows.getValue()[0].toPlainText());
		verify(console, never()).sendMessage(Messages.NOT_PLAYER.toString());
	}

	@Test
	@DisplayName("consoleInfoByName_runs")
	void consoleInfoByName_runs() {
		command().runExecute("glw", console, new String[]{"gang", "info", "Rats"});

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(console, org.mockito.Mockito.atLeastOnce()).sendMessage(lines.capture());
		assertTrue(lines.getAllValues().stream().anyMatch(line -> line.contains("Rats")), lines.getAllValues().toString());
		assertTrue(lines.getAllValues().stream().noneMatch(Messages.NOT_PLAYER.toString()::equals));
	}

	@Test
	@DisplayName("consoleCreate_refusedOnce")
	void consoleCreate_refusedOnce() {
		command().runExecute("glw", console, new String[]{"gang", "create", "Rats"});

		verify(console, times(1)).sendMessage(anyString());
		verify(console).sendMessage(Messages.NOT_PLAYER.toString());
		verifyNoInteractions(gangManager);
	}

	@Test
	@DisplayName("playerInDisabledWorld_getsOneLineAndNothingRuns")
	void playerInDisabledWorld_getsOneLineAndNothingRuns() {
		World nether = mock(World.class);
		when(nether.getName()).thenReturn("world_nether");
		Player player = mock(Player.class);
		when(player.hasPermission(anyString())).thenReturn(true);
		when(player.getWorld()).thenReturn(nether);

		command().runExecute("glw", player, new String[]{"gang", "list"});

		verify(player, times(1)).sendMessage(anyString());
		verify(player).sendMessage(messages.text("Worlds.Disabled_Here", "&cGangs are disabled in this world."));
		verifyNoInteractions(gangManager);
	}

	@Test
	@DisplayName("gangEnableFalse_turnsEverythingOff")
	void gangEnableFalse_turnsEverythingOff() {
		settings.when(Settings::isGangEnabled).thenReturn(false);

		command().runExecute("glw", console, new String[]{"gang", "list"});

		verify(console, times(1)).sendMessage(anyString());
		verify(console).sendMessage(messages.text("Worlds.Gangs_Off", "&cGangs are turned off on this server."));
	}
}
