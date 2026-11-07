package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
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
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Pins how {@code /glw gang chat}, {@code /glw gang recruiting} and {@code /glw gang upgrade} resolve the sender's
 * gang: a console sender, a sender with no cached user, no gang, or a gang id that no longer resolves each get the
 * must-create-a-gang line once and nothing else happens; a resolvable member goes on to the command's own reply.
 */
@DisplayName("Gang chat, recruiting and upgrade - resolving the sender's gang")
class GangSenderGangTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private GangManager            gangManager;
	private UserManager<Player>    userManager;
	private GangChatToggles        channel;
	private Player                player;
	private User<Player>           user;
	private Gang                   gang;
	private Argument               chat;
	private Argument               recruiting;
	private Argument               upgrade;

	@SuppressWarnings("unchecked")
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
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of());
		Command.setInformationManager(mock(InformationManager.class));
		messages = W2P2Messages.shipped(tempDir);

		gangManager = mock(GangManager.class);
		userManager = mock(UserManager.class);
		MemberManager memberManager = mock(MemberManager.class);
		channel = new GangChatToggles();

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(4);
		when(userManager.getUser(player)).thenReturn(user);

		gang = mock(Gang.class);
		when(gang.getDisplayNameString()).thenReturn("Rats");
		when(gang.getState()).thenReturn(Gang.State.OPEN);
		Level top = mock(Level.class);
		when(top.getLevelValue()).thenReturn(5);
		when(top.getMaxLevel()).thenReturn(5);
		when(gang.getLevel()).thenReturn(top);
		when(gangManager.getGang(4)).thenReturn(gang);

		chat = new GangChatCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                           memberManager, gangManager, messages, channel);
		recruiting = new GangRecruitingCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                       userManager, memberManager, gangManager, messages);
		upgrade = new GangUpgradeCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                 mock(GangLimits.class), mock(GangLevelTable.class), gangManager, userManager,
		                                 memberManager, messages);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("a console sender is told to create a gang and nothing is looked up")
	void console_isToldToCreateAGang() {
		for (Argument command : List.of(chat, recruiting, upgrade)) {
			ConsoleCommandSender console = mock(ConsoleCommandSender.class);

			command.executeArgument(console, new String[]{"gang", "x"});

			verify(console, times(1)).sendMessage(anyString());
			verify(console).sendMessage(Messages.MUST_CREATE_GANG.toString());
		}
		verifyNoInteractions(userManager);
		verifyNoInteractions(gangManager);
	}

	@Test
	@DisplayName("a player with no cached user is told to create a gang")
	void noUser_isToldToCreateAGang() {
		when(userManager.getUser(player)).thenReturn(null);

		for (Argument command : List.of(chat, recruiting, upgrade)) {
			command.executeArgument(player, new String[]{"gang", "x"});
		}

		verify(player, times(3)).sendMessage(Messages.MUST_CREATE_GANG.toString());
		verify(player, times(3)).sendMessage(anyString());
		verifyNoInteractions(gangManager);
	}

	@Test
	@DisplayName("a player with no gang is told to create a gang and the gang is not looked up")
	void noGang_isToldToCreateAGang() {
		when(user.hasGang()).thenReturn(false);

		for (Argument command : List.of(chat, recruiting, upgrade)) {
			command.executeArgument(player, new String[]{"gang", "x"});
		}

		verify(player, times(3)).sendMessage(Messages.MUST_CREATE_GANG.toString());
		verify(player, times(3)).sendMessage(anyString());
		verifyNoInteractions(gangManager);
	}

	@Test
	@DisplayName("a gang id that no longer resolves is told to create a gang")
	void staleGangId_isToldToCreateAGang() {
		when(gangManager.getGang(4)).thenReturn(null);

		for (Argument command : List.of(chat, recruiting, upgrade)) {
			command.executeArgument(player, new String[]{"gang", "x"});
		}

		verify(player, times(3)).sendMessage(Messages.MUST_CREATE_GANG.toString());
		verify(player, times(3)).sendMessage(anyString());
	}

	@Test
	@DisplayName("a member with a resolvable gang is not refused and reaches each command's own reply")
	void resolvableGang_reachesTheCommandReply() {
		chat.executeArgument(player, new String[]{"gang", "chat"});
		verify(player).sendMessage(messages.text("Social.Chat_On",
		                                         "&aGang chat on: what you type goes to your gang only. "
		                                         + "&7/glw gang chat again to switch it off."));

		recruiting.executeArgument(player, new String[]{"gang", "recruiting"});
		verify(player).sendMessage(messages.text("Social.Recruiting_Now",
		                                         "&7%gang% is %state%. &7Use /glw gang recruiting open|invite to change it.",
		                                         "gang", "Rats", "state", "open"));

		upgrade.executeArgument(player, new String[]{"gang", "upgrade"});
		verify(player).sendMessage(messages.text("Levels.Max", "&7Your gang is already at the highest level."));

		verify(player, never()).sendMessage(Messages.MUST_CREATE_GANG.toString());
	}

}
