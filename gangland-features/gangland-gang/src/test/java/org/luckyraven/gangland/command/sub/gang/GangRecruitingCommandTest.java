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
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.rank.Rank;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** {@code /glw gang recruiting <open|invite>}: the leader (or a rank with the invite node) sets the gang's state (W2-P2). */
@DisplayName("GangRecruitingCommand - /glw gang recruiting <open|invite>")
class GangRecruitingCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private GangManager            gangManager;
	private UserManager<Player>    userManager;
	private MemberManager          memberManager;
	private Player                 player;
	private UUID                   playerId;
	private User<Player>           user;
	private Gang                   vipers;
	private User<Player>           mate;

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

		gangManager   = mock(GangManager.class);
		userManager   = mock(UserManager.class);
		memberManager = mock(MemberManager.class);

		playerId = UUID.randomUUID();
		player   = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Rex");

		user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(1);
		when(userManager.getUser(player)).thenReturn(user);
		when(gangManager.getGang(1)).thenAnswer(invocation -> vipers);

		vipers = mock(Gang.class);
		when(vipers.getId()).thenReturn(1);
		when(vipers.getDisplayNameString()).thenReturn("Vipers");
		when(vipers.getState()).thenReturn(Gang.State.INVITE);
		mate = mock(User.class);
		when(vipers.getOnlineMembers(any())).thenReturn(List.of(mate));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("the leader sets open, and the gang's online members are told")
	void leader_setsOpen() throws Exception {
		setMember(leaderRank());

		recruiting().executeArgument(player, new String[]{"gang", "recruiting", "open"});

		verify(vipers).setState(Gang.State.OPEN);
		verify(mate).sendMessage(messages.text("Social.Recruiting_Open",
		                                       "&a%gang% is open: anyone can /glw gang join it.", "gang", "Vipers"));
	}

	@Test
	@DisplayName("a member whose rank lacks the invite node is refused and the state is unchanged")
	void memberWithoutInviteNode_refused() throws Exception {
		setMember(memberRank());

		recruiting().executeArgument(player, new String[]{"gang", "recruiting", "open"});

		verify(player).sendMessage(Messages.COMMAND_NO_PERM.toString());
		verify(vipers, never()).setState(any());
	}

	@Test
	@DisplayName("a member with the invite node may change the state")
	void memberWithInviteNode_setsInvite() throws Exception {
		when(player.hasPermission(GangPermissions.INVITE)).thenReturn(true);
		setMember(memberRank());
		when(vipers.getState()).thenReturn(Gang.State.OPEN);

		recruiting().executeArgument(player, new String[]{"gang", "recruiting", "invite"});

		verify(vipers).setState(Gang.State.INVITE);
		verify(mate).sendMessage(messages.text("Social.Recruiting_Invite", "&7%gang% is invite only.", "gang", "Vipers"));
	}

	@Test
	@DisplayName("with no argument the current state is shown, and nothing changes")
	void noArgument_showsState() throws Exception {
		when(vipers.getState()).thenReturn(Gang.State.OPEN);

		recruiting().executeArgument(player, new String[]{"gang", "recruiting"});

		verify(player).sendMessage(messages.text("Social.Recruiting_Now",
		                                         "&7%gang% is %state%. &7Use /glw gang recruiting open|invite to change it.",
		                                         "gang", "Vipers", "state", "open"));
		verify(vipers, never()).setState(any());
	}

	@Test
	@DisplayName("a gangless player is told to create a gang")
	void gangless_mustCreateGang() throws Exception {
		when(user.hasGang()).thenReturn(false);

		recruiting().executeArgument(player, new String[]{"gang", "recruiting", "open"});

		verify(player).sendMessage(Messages.MUST_CREATE_GANG.toString());
		verify(vipers, never()).setState(any());
	}

	@Test
	@DisplayName("the state argument completes to open and invite")
	void stateArgument_completesOpenAndInvite() {
		assertEquals(List.of("open", "invite"), recruiting().getNode().getChildren().get(0).getData()
				.getArgumentString(player));
	}

	private Argument recruiting() {
		return new GangRecruitingCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                                 memberManager, gangManager, messages);
	}

	private void setMember(Rank rank) {
		Member member = mock(Member.class);
		when(member.getRank()).thenReturn(rank);
		when(memberManager.getMember(playerId)).thenReturn(member);
	}

	/** A rank with no children is the leader: {@link GangPermissions#isTopRank} holds for it. */
	private static Rank leaderRank() {
		return new Rank("owner", 1);
	}

	/** A rank with a child below it is not the leader, so it needs the invite node. */
	private static Rank memberRank() {
		Rank member = new Rank("member", 2);
		member.getNode().add(new Rank("officer", 3).getNode());
		return member;
	}

}
