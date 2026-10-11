package org.luckyraven.gangland.command.sub.gang;

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
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** {@code /glw gang chat [message]}: a message goes to the gang now; no message toggles gang chat mode (W2-P2). */
@DisplayName("GangChatCommand - /glw gang chat [message]")
class GangChatCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private GangManager            gangManager;
	private UserManager<Player>    userManager;
	private MemberManager          memberManager;
	private GangChatToggles        channel;
	private Player                 rex;
	private Player                 mate;
	private Player                 foe;
	private UUID                   rexId;
	private User<Player>           rexUser;

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
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(Settings::getLanguagePicked).thenReturn("en");

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of(rex, mate, foe));

		Command.setInformationManager(mock(InformationManager.class));
		messages = W2P2Messages.shipped(tempDir);

		gangManager   = mock(GangManager.class);
		userManager   = mock(UserManager.class);
		memberManager = mock(MemberManager.class);
		channel       = new GangChatToggles();

		Gang vipers = mock(Gang.class);
		when(vipers.getId()).thenReturn(1);
		when(gangManager.getGang(1)).thenReturn(vipers);

		rexId = UUID.randomUUID();
		rex   = member("Rex", rexId, 1);
		mate  = member("Mate", UUID.randomUUID(), 1);
		foe   = member("Foe", UUID.randomUUID(), 2);
		rexUser = userManager.getUser(rex);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("with a message the line goes to the gang's online members and the toggle is not changed")
	void withMessage_deliversToGang() throws Exception {
		messageArgument().executeArgument(rex, new String[]{"gang", "chat", "hello", "there"});

		String line = channel.format(messages, "Rex", "hello there");
		verify(rex).sendMessage(line);
		verify(mate).sendMessage(line);
		verify(foe, never()).sendMessage(anyString());
		assertFalse(channel.isOn(rexId));
	}

	@Test
	@DisplayName("a multi-word message dispatched through the real argument tree reaches the gang as one line")
	void multiWord_throughTree_deliversOneLine() throws Exception {
		when(rex.hasPermission(anyString())).thenReturn(true);
		Tree<Argument> tree = new Tree<>();
		Argument       gang = new Argument(mock(JavaPlugin.class), "gang", tree, null, "gang");
		tree.add(gang.getNode());
		gang.addSubArgument(chat(tree, gang));

		gang.execute("glw", rex, new String[]{"gang", "chat", "hello", "there"});

		String line = channel.format(messages, "Rex", "hello there");
		verify(rex).sendMessage(line);
		verify(mate).sendMessage(line);
		verify(foe, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("without a message the first use switches gang chat on and says so")
	void noMessage_togglesOn() throws Exception {
		chat().executeArgument(rex, new String[]{"gang", "chat"});

		assertTrue(channel.isOn(rexId));
		verify(rex).sendMessage(messages.text("Social.Chat_On", "&aGang chat on."));
	}

	@Test
	@DisplayName("the second use with no message switches gang chat off again")
	void noMessage_togglesOff() throws Exception {
		channel.toggle(rexId);

		chat().executeArgument(rex, new String[]{"gang", "chat"});

		assertFalse(channel.isOn(rexId));
		verify(rex).sendMessage(messages.text("Social.Chat_Off", "&7Gang chat off."));
	}

	@Test
	@DisplayName("a gangless sender is told to create a gang, with or without a message")
	void gangless_mustCreateGang() throws Exception {
		when(rexUser.hasGang()).thenReturn(false);

		chat().executeArgument(rex, new String[]{"gang", "chat"});
		messageArgument().executeArgument(rex, new String[]{"gang", "chat", "hi"});

		verify(rex, times(2)).sendMessage(Messages.MUST_CREATE_GANG.toString());
		assertFalse(channel.isOn(rexId));
	}

	private Argument chat() {
		return chat(new Tree<>(), mock(Argument.class));
	}

	private Argument chat(Tree<Argument> tree, Argument parent) {
		return new GangChatCommand(mock(JavaPlugin.class), tree, parent, userManager,
		                           memberManager, gangManager, messages, channel);
	}

	/** The {@code <message>} child of {@code chat}; a message is sent through it, not the parent. */
	private Argument messageArgument() {
		// children: the literal "allies" stub first, the optional <message> last
		return chat().getNode().getChildren().get(1).getData();
	}

	/** A player in gang {@code gangId}, known to the user manager and to the online list. */
	@SuppressWarnings("unchecked")
	private Player member(String name, UUID id, int gangId) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.getUniqueId()).thenReturn(id);

		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(gangId);
		when(userManager.getUser(player)).thenReturn(user);
		return player;
	}

}
