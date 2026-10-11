package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.Bukkit;
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
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Pins the GR-23 follow-up: a bare {@code /glw gang accept} with several pending invites must not auto-accept one
 * (accepting cancels the others, so the "pick a gang" instruction would be impossible). Both invites stay pending and
 * the player stays out of every gang.
 */
@DisplayName("GangInviteAcceptCommand - bare accept with several pending invites")
class GangInviteAcceptCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private BukkitStatics          statics;
	private FakeMailRepositoryContract repo;
	private MailManager            mailManager;
	private UUID                   uuid;
	private Player                 player;
	private User<Player>           user;
	private Gang                   gangTwo;
	private Gang                   gangThree;
	private GangInviteAcceptCommand accept;

	@BeforeEach
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
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		statics = BukkitStatics.install();
		statics.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of());

		uuid   = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(false);

		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		gangTwo = mock(Gang.class);
		when(gangTwo.getId()).thenReturn(2);
		when(gangTwo.getDisplayNameString()).thenReturn("gang2");
		gangThree = mock(Gang.class);
		when(gangThree.getId()).thenReturn(3);
		when(gangThree.getDisplayNameString()).thenReturn("gang3");
		GangManager gangManager = mock(GangManager.class);
		when(gangManager.getGang(2)).thenReturn(gangTwo);
		when(gangManager.getGang(3)).thenReturn(gangThree);

		repo        = new FakeMailRepositoryContract();
		mailManager = new MailManager(repo);
		mailManager.initialize();

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, "mail_messages", "mail", ".yml",
		                                    GangInviteAcceptCommandTest.class.getClassLoader()), true);
		MailMessages messages = new MailMessages(fileManager);

		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");

		accept = new GangInviteAcceptCommand(plugin, tree, parent, userManager, mock(MemberManager.class),
		                                     gangManager, mock(RankManager.class), mailManager, messages,
		                                     mock(GangMembershipService.class));
	}

	@AfterEach
	void tearDown() {
		settings.close();
		statics.close();
	}

	@Test
	@DisplayName("bare accept with two pending invites accepts neither and asks for the gang")
	void accept_bareWithTwoPendingInvites_acceptsNeither() {
		MailItem fromTwo   = invite(2);
		MailItem fromThree = invite(3);

		accept.action().accept(accept, player, new String[]{"gang", "accept"});

		assertEquals(MailStatus.PENDING, fromTwo.getStatus());
		assertEquals(MailStatus.PENDING, fromThree.getStatus());
		assertEquals(2, mailManager.findPendingForRecipient(uuid, MailType.GANG_INVITE).size());
		verify(gangTwo, never()).addMember(any(), any(), any());
		verify(gangThree, never()).addMember(any(), any(), any());
		verify(user, atLeastOnce()).sendMessage(anyString());
		assertTrue(repo.deleted().isEmpty());
	}

	@Test
	@DisplayName("accept with a gang name that has no pending invite says no invite from it, not 'must be numbers'")
	void accept_withNameWithoutInvite_saysNoInviteFrom() {
		invite(2);
		when(player.hasPermission(anyString())).thenReturn(true);

		optionalChild(accept).executeArgument(player, new String[]{"gang", "accept", "Cats"});

		verify(user).sendMessage(Messages.GANG_INVITE_NO_INVITE_FROM.toString().replace("%gang%", "Cats"));
		verify(user, never()).sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", "Cats"));
		assertEquals(1, mailManager.findPendingForRecipient(uuid, MailType.GANG_INVITE).size());
	}

	private static Argument optionalChild(Argument parent) {
		return parent.getNode().getChildren().stream().map(Tree.Node::getData)
		             .filter(OptionalArgument.class::isInstance).findFirst().orElseThrow();
	}

	private MailItem invite(int senderGangId) {
		MailItem invite = new MailItem(mailManager.allocateId(), MailType.GANG_INVITE, null, senderGangId, uuid,
		                               MailItem.NO_GANG, null, System.currentTimeMillis(), 0L, MailStatus.PENDING,
		                               false);
		mailManager.send(invite);
		return invite;
	}

}
