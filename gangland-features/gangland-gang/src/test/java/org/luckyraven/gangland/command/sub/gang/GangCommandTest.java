package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
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
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Bare {@code /glw gang} only reaches {@code onExecute} when the {@code gang_info.yml} menu did not take the command
 * (no inventory permission, or no such menu). A gangless player gets the onboarding screen; a member gets the help
 * page, as before.
 */
@DisplayName("GangCommand - bare /glw gang answers: onboarding when gangless, help for members")
class GangCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;

	@BeforeEach
	void setUp() throws IOException {
		GangSettings.bind(new FakeGangSettingsContract().withCreateFee(new BigDecimal("2500")));
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
	@DisplayName("a gangless player gets the three onboarding lines and the two buttons, not the help page")
	@SuppressWarnings("unchecked")
	void gangless_getsOnboarding() {
		Player              player      = mock(Player.class);
		Player.Spigot       spigot      = mock(Player.Spigot.class);
		User<Player>        user        = mock(User.class);
		UserManager<Player> userManager = mock(UserManager.class);
		when(player.spigot()).thenReturn(spigot);
		when(user.hasGang()).thenReturn(false);
		when(userManager.getUser(player)).thenReturn(user);

		GangCommand command = command(userManager);
		command.onExecute(command.getArgument(), player, new String[]{"gang"});

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(player, times(2)).sendMessage(lines.capture());
		List<String> text = lines.getAllValues().stream().map(ChatColor::stripColor).toList();
		assertTrue(text.get(0).contains("team up to hold turf"), text.toString());
		assertTrue(text.get(1).contains("costs $" + Settings.formatAmount(new BigDecimal("2500"))), text.toString());

		ArgumentCaptor<BaseComponent[]> buttons = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(spigot).sendMessage(buttons.capture());
		assertTrue(hasClick(buttons.getValue(), ClickEvent.Action.RUN_COMMAND, "/glw gang list"));
		assertTrue(hasClick(buttons.getValue(), ClickEvent.Action.SUGGEST_COMMAND, "/glw gang create "));
		verify(player, never()).sendMessage(Messages.COMMAND_HELP_EMPTY.toString());
	}

	@Test
	@DisplayName("a member still gets the help page")
	@SuppressWarnings("unchecked")
	void member_getsHelp() {
		Player              player      = mock(Player.class);
		User<Player>        user        = mock(User.class);
		UserManager<Player> userManager = mock(UserManager.class);
		when(user.hasGang()).thenReturn(true);
		when(userManager.getUser(player)).thenReturn(user);

		GangCommand command = command(userManager);
		command.onExecute(command.getArgument(), player, new String[]{"gang"});

		verify(player).sendMessage(Messages.COMMAND_HELP_EMPTY.toString());
	}

	@Test
	@DisplayName("a sender with no cached user still gets the help page")
	@SuppressWarnings("unchecked")
	void bareGangCommand_showsHelp_whenNoUser() {
		Player              player      = mock(Player.class);
		UserManager<Player> userManager = mock(UserManager.class);

		GangCommand command = command(userManager);
		command.onExecute(command.getArgument(), player, new String[]{"gang"});

		verify(player).sendMessage(Messages.COMMAND_HELP_EMPTY.toString());
	}

	private GangCommand command(UserManager<Player> userManager) {
		return new GangCommand(mock(JavaPlugin.class), userManager, mock(UserManager.class), null, null, null, null,
		                       null, mock(DependencyContainer.class), null, mock(GangRules.class), messages,
		                       new GangChatToggles(), new GangWorldPolicy(mock(GangRules.class)), new GangMembershipService(),
		                       new GangDisbandService(), new GangLeadership(), new CombatTag(),
		                       new GangStatsService(), new GangLimits(), new GangLevelTable(),
		                       mock(GangSafehouseService.class), mock(FriendlyFirePolicy.class));
	}

	private static boolean hasClick(BaseComponent[] components, ClickEvent.Action action, String value) {
		for (BaseComponent component : components) {
			ClickEvent click = component.getClickEvent();
			if (click != null && click.getAction() == action && value.equals(click.getValue())) return true;
		}
		return false;
	}

}
