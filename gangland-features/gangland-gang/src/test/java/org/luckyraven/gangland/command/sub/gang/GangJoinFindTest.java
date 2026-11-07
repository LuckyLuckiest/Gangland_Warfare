package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.gang.support.MembershipKit;
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
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Pins the name match behind {@code /glw gang join <gang>}: plain name, ignoring case and colour codes, first gang wins. */
@DisplayName("GangJoinCommand - which gang a typed name selects")
class GangJoinFindTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private PluginManager          pluginManager;
	private GangMessages           messages;
	private GangManager            gangManager;
	private UserManager<Player>    userManager;
	private MemberManager          memberManager;
	private RankManager            rankManager;
	private Player                 player;
	private UUID                   playerId;
	private User<Player>           user;
	private Member                 member;
	private Rank                   head;
	private final List<Gang>       gangs = new ArrayList<>();

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

		pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of());

		Command.setInformationManager(mock(InformationManager.class));
		messages = W2P2Messages.shipped(tempDir);

		gangManager   = mock(GangManager.class);
		userManager   = mock(UserManager.class);
		memberManager = mock(MemberManager.class);
		rankManager   = mock(RankManager.class);

		head = new Rank("member", 1);
		when(rankManager.get(anyString())).thenReturn(head);
		when(gangManager.getAll()).thenAnswer(invocation -> gangs);

		playerId = UUID.randomUUID();
		player   = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Rex");
		bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		user = user(false);
		when(userManager.getUser(player)).thenReturn(user);
		member = mock(Member.class);
		when(memberManager.getMember(playerId)).thenReturn(member);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("the first gang in order whose plain name matches wins; a null-named gang is skipped, not an NPE")
	void firstPlainNameMatchWins_nullNamesSkipped() {
		Gang nameless = added(null);
		Gang other    = added("Kings");
		Gang coloured = added("&cRats");
		Gang plain    = added("rats");

		nameArgument().executeArgument(player, new String[]{"gang", "join", "rATS"});

		verify(coloured).addMember(user, member, head);
		verify(plain, never()).addMember(any(User.class), any(Member.class), any(Rank.class));
		verify(nameless, never()).addMember(any(User.class), any(Member.class), any(Rank.class));
		verify(other, never()).addMember(any(User.class), any(Member.class), any(Rank.class));
	}

	@Test
	@DisplayName("colour codes typed in the name are ignored when matching")
	void typedColourCodes_areIgnored() {
		added("Kings");
		Gang rats = added("Rats");

		nameArgument().executeArgument(player, new String[]{"gang", "join", "&aRats"});

		verify(rats).addMember(user, member, head);
	}

	@Test
	@DisplayName("gangs that do not match leave the name unknown and the refusal names the plain text")
	void nonMatchingGangs_giveTheUnknownRefusal() {
		added(null);
		Gang kings = added("Kings");

		nameArgument().executeArgument(player, new String[]{"gang", "join", "&aNope"});

		verify(player).sendMessage(messages.text("Social.Unknown", "&cNo gang named %gang%.", "gang", "Nope"));
		verify(kings, never()).addMember(any(User.class), any(Member.class), any(Rank.class));
	}

	/** An open gang with a distinct id, appended to the manager's list in call order. */
	private Gang added(String name) {
		Gang gang = gang(name, Gang.State.OPEN);
		gangs.add(gang);
		return gang;
	}

	private Argument join() {
		return new GangJoinCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                           memberManager, gangManager, rankManager, messages,
		                           new MembershipKit(gangManager, memberManager, rankManager, userManager,
		                                             mock(UserManager.class), mock(RepositoryRegistry.class),
		                                             messages).service);
	}

	private Argument nameArgument() {
		return join().getNode().getChildren().get(0).getData();
	}

	private Gang gang(String name, Gang.State state) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(100 + gangs.size());
		when(gangManager.getGang(100 + gangs.size())).thenReturn(gang);
		when(gang.getName()).thenReturn(name);
		when(gang.getDisplayNameString()).thenReturn(name);
		when(gang.getState()).thenReturn(state);
		when(gang.getOnlineMembers(any())).thenReturn(List.of());
		return gang;
	}

	@SuppressWarnings("unchecked")
	private static User<Player> user(boolean hasGang) {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(hasGang);
		return user;
	}

}
