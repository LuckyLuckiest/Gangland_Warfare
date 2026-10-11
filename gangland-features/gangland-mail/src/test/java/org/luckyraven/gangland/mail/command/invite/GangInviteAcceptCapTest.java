package org.luckyraven.gangland.mail.command.invite;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/** An accepted invite goes through the membership service, so a full gang refuses it and says why. */
@DisplayName("GangInviteAcceptCommand - the member cap applies at accept")
class GangInviteAcceptCapTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private BukkitStatics          statics;
	private MailManager            mailManager;
	private UUID                   uuid;
	private Player                 player;
	private User<Player>           user;
	private Gang                   gang;
	private GangInviteAcceptCommand accept;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
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
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		statics = BukkitStatics.install();
		statics.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of());

		uuid   = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		statics.statics().when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(false);
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		// a gang of one whose level allows one member
		gang = mock(Gang.class);
		when(gang.getId()).thenReturn(2);
		when(gang.getDisplayNameString()).thenReturn("Vipers");
		when(gang.getMembers()).thenReturn(List.of(new Member(UUID.randomUUID())));
		GangManager gangManager = mock(GangManager.class);
		when(gangManager.getGang(2)).thenReturn(gang);

		MemberManager memberManager = mock(MemberManager.class);
		when(memberManager.getMember(uuid)).thenReturn(new Member(uuid));
		RankManager rankManager = mock(RankManager.class);
		when(rankManager.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(new Rank("member", 1));

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, "mail_messages", "mail", ".yml",
		                                    GangInviteAcceptCapTest.class.getClassLoader()), true);
		fileManager.addFile(new FileHandler(plugin, "gang_messages", "gang", ".yml",
		                                    GangInviteAcceptCapTest.class.getClassLoader()), true);
		MailMessages mailMessages = new MailMessages(fileManager);
		GangMessages gangMessages = new GangMessages(fileManager);

		GangLimits limits = new GangLimits() {
			@Override
			public int maxMembers(int gangId) {
				return 1;
			}
		};
		GangRules rules = mock(GangRules.class);
		when(rules.getJoinCommands()).thenReturn(List.of());
		GangMembershipService service = new GangMembershipService(gangManager, memberManager, rankManager,
		                                                          userManager, mock(UserManager.class), limits,
		                                                          new CombatTag(), rules, gangMessages,
		                                                          mock(GangDisbandService.class),
		                                                          mock(GangLeadership.class), line -> { });

		mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();

		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		accept = new GangInviteAcceptCommand(plugin, new Tree<>(), parent, userManager, memberManager, gangManager,
		                                     rankManager, mailManager, mailMessages, service);
	}

	@AfterEach
	void tearDown() {
		settings.close();
		statics.close();
	}

	@Test
	@DisplayName("accept_refusedWhenFull")
	void accept_refusedWhenFull() {
		MailItem invite = new MailItem(mailManager.allocateId(), MailType.GANG_INVITE, null, 2, uuid,
		                               MailItem.NO_GANG, null, System.currentTimeMillis(), 0L, MailStatus.PENDING,
		                               false);
		mailManager.send(invite);

		accept.action().accept(accept, player, new String[]{"gang", "accept"});

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(player, atLeastOnce()).sendMessage(lines.capture());
		assertTrue(lines.getAllValues().stream().anyMatch(
				line -> ChatColor.stripColor(line).equals("Vipers is full (1/1).")), lines.getAllValues().toString());
		verify(gang, never()).addMember(any(User.class), any(Member.class), any(Rank.class));
		assertEquals(MailStatus.PENDING, invite.getStatus(), "the invite stays; the player may try again later");
	}

}
