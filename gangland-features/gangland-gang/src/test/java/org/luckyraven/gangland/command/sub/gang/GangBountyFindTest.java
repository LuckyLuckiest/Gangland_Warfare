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
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pins the target match behind {@code /glw gang bounty <gang> <amount>}: the plain name (colour codes stripped,
 * ignoring case), the first gang in order wins, a gang with no name is skipped, ids are never accepted. The chosen
 * gang shows through the own-gang refusal, which compares the poster's gang id with the target's id.
 */
@DisplayName("GangBountyCommand - which gang a typed name selects")
class GangBountyFindTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private GangManager            gangManager;
	private Player                 player;
	private User<Player>           user;
	private Argument               bounty;
	private final List<Gang>       gangs = new ArrayList<>();

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() throws Exception {
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

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of());

		Command.setInformationManager(mock(InformationManager.class));
		messages = GangTestMessages.shipped(tempDir);

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(anyString())).thenReturn(true);
		user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);
		gangManager = mock(GangManager.class);
		when(gangManager.getAll()).thenAnswer(invocation -> gangs);

		bounty = new GangBountyCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                               mock(MemberManager.class), gangManager, messages).getNode()
		                                                                                .getChildren()
		                                                                                .get(0)
		                                                                                .getData()
		                                                                                .getNode()
		                                                                                .getChildren()
		                                                                                .get(0)
		                                                                                .getData();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("the first gang whose plain name matches ignoring case is the target; a nameless gang is skipped")
	void firstPlainNameMatchWins_namelessSkipped() {
		gang(1, null);
		gang(2, "Kings");
		gang(3, "&cRats");
		gang(4, "rats");

		post(3, "rATS");

		verify(player).sendMessage(messages.text("Bounty.Own_Gang", "&cYou cannot put a bounty on your own gang."));
	}

	@Test
	@DisplayName("a later gang with the same plain name is not the target")
	void laterCollidingGang_isNotTheTarget() {
		gang(3, "&cRats");
		gang(4, "rats");

		post(4, "Rats");

		verify(player, never()).sendMessage(
				messages.text("Bounty.Own_Gang", "&cYou cannot put a bounty on your own gang."));
		verify(player).sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", "x"));
	}

	@Test
	@DisplayName("colour codes typed in the name are ignored, and an id is not accepted as a name")
	void typedColourCodesIgnored_idsNotAccepted() {
		gang(5, "Rats");

		post(5, "&aRats");
		verify(player).sendMessage(messages.text("Bounty.Own_Gang", "&cYou cannot put a bounty on your own gang."));

		post(5, "5");
		verify(player).sendMessage(messages.text("Bounty.Unknown", "&cNo gang named %gang%.", "gang", "5"));
	}

	@Test
	@DisplayName("no matching gang gives the unknown refusal with the plain typed text")
	void noMatch_givesTheUnknownRefusal() {
		gang(1, null);
		gang(2, "Kings");

		post(2, "&aNope");

		verify(player).sendMessage(messages.text("Bounty.Unknown", "&cNo gang named %gang%.", "gang", "Nope"));
	}

	private void post(int posterGangId, String typedName) {
		when(user.getGangId()).thenReturn(posterGangId);
		bounty.executeArgument(player, new String[]{"gang", "bounty", typedName, "x"});
	}

	private Gang gang(int id, String name) {
		Gang gang = new Gang(id);
		gang.setName(name);
		when(gangManager.getGang(id)).thenReturn(gang);
		gangs.add(gang);
		return gang;
	}

}
