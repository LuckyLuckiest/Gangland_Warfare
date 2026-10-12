package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

@DisplayName("GangKickCommand - an offline target with no loaded row is load-pending, not defaults-to-save")
class GangKickCommandTest {

	private static final int GANG_ID = 7;

	private MockedStatic<Bukkit>            bukkit;
	private MockedStatic<Settings>          settings;
	private JavaPlugin                      plugin;
	private UserManager<Player>             userManager;
	private UserManager<OfflinePlayer>      offlineUserManager;
	private MemberManager                   memberManager;
	private GangManager                     gangManager;
	private RankManager                     rankManager;
	private Tree<Rank>                      rankTree;
	private Argument                        parent;
	private GangKickCommand                 command;
	private Gang                            gang;
	private Rank                            ownerRank;
	private Rank                            memberRank;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return "";
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});

		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		bukkit = mockStatic(Bukkit.class);
		Server server = mock(Server.class);
		when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

		plugin             = mock(JavaPlugin.class);
		userManager        = mock(UserManager.class);
		offlineUserManager = mock(UserManager.class);
		memberManager      = mock(MemberManager.class);
		gangManager        = mock(GangManager.class);
		rankManager        = mock(RankManager.class);
		rankTree           = mock(Tree.class);

		ownerRank  = new Rank("owner", 1);
		memberRank = new Rank("member", 2);
		doReturn(rankTree).when(rankManager).getRankTree();
		when(rankTree.isDescendant(any(), any())).thenReturn(true);

		gang = new Gang(GANG_ID);
		gang.setName("Crew");
		when(gangManager.getGang(GANG_ID)).thenReturn(gang);

		parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");

		command = new GangKickCommand(plugin, new Tree<>(), parent, userManager, offlineUserManager,
		                              memberManager, gangManager, rankManager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("kicking an offline member whose row never loaded caches a load-pending fallback user")
	@SuppressWarnings("unchecked")
	void kickOfflineTarget_withoutLoadedRow_isLoadPending() {
		UUID                bobUuid = UUID.randomUUID();
		OfflinePlayer       bob     = mock(OfflinePlayer.class);
		when(bob.getName()).thenReturn("Bob");
		when(bob.getUniqueId()).thenReturn(bobUuid);
		bukkit.when(() -> Bukkit.getOfflinePlayer(bobUuid)).thenReturn(bob);

		User<OfflinePlayer> bobUser = mock(User.class);
		AtomicBoolean       pending = new AtomicBoolean();
		doAnswer(i -> {
			pending.set(i.getArgument(0));
			return null;
		}).when(bobUser).setLoadPending(anyBoolean());
		when(bobUser.isLoadPending()).thenAnswer(i -> pending.get());

		// no cached row for bob: the command falls back to create(), as it does when the boot read failed
		when(offlineUserManager.getUser(any(OfflinePlayer.class))).thenReturn(null);
		doReturn(bobUser).when(offlineUserManager).create(any(OfflinePlayer.class));

		Member bobMember = new Member(bobUuid);
		gang.addMember(bobMember, memberRank);
		when(memberManager.getMember(bobUuid)).thenReturn(bobMember);

		Player       alice     = mock(Player.class);
		UUID         aliceUuid = UUID.randomUUID();
		User<Player> aliceUser = mock(User.class);
		when(alice.getUniqueId()).thenReturn(aliceUuid);
		when(userManager.getUser(alice)).thenReturn(aliceUser);
		when(aliceUser.hasGang()).thenReturn(true);
		when(aliceUser.getGangId()).thenReturn(GANG_ID);
		Member aliceMember = new Member(aliceUuid);
		gang.addMember(aliceMember, ownerRank);
		when(memberManager.getMember(aliceUuid)).thenReturn(aliceMember);

		Argument kickName = (Argument) command.getNode().getChildren().get(0).getData();
		kickName.executeArgument(alice, new String[]{"gang", "kick", "Bob"});

		verify(offlineUserManager).add(bobUser);
		verify(bobUser).setLoadPending(true);
		assertTrue(bobUser.isLoadPending(), "the fallback user must stay load-pending");
		assertFalse(gang.getMembers().contains(bobMember), "bob is removed from the gang");
	}

}
