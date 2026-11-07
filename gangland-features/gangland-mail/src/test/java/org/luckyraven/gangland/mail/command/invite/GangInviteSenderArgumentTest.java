package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Characterises which pending invite {@code /glw gang accept <id>} and {@code /glw gang decline <id>} act on when the
 * player names a sender gang by id: the first pending invite from that gang (cache order), the others untouched or
 * cancelled as each command always did.
 */
@DisplayName("Gang invite accept/decline - picking the invite by sender gang id")
class GangInviteSenderArgumentTest {

	private MockedStatic<Settings>    settings;
	private BukkitStatics             statics;
	private FakeMailRepositoryContract repo;
	private MailManager               mailManager;
	private UUID                      uuid;
	private Player                    player;
	private User<Player>              user;
	private GangMembershipService     membership;
	private GangInviteAcceptCommand   accept;
	private GangInviteDeclineCommand  decline;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
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
		when(player.hasPermission(anyString())).thenReturn(true);

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(false);
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		GangManager gangManager = mock(GangManager.class);
		for (int id : new int[]{2, 3}) {
			Gang gang = mock(Gang.class);
			when(gang.getId()).thenReturn(id);
			when(gang.getName()).thenReturn("gang" + id);
			when(gang.getDisplayNameString()).thenReturn("gang" + id);
			when(gangManager.getGang(id)).thenReturn(gang);
		}

		repo        = new FakeMailRepositoryContract();
		mailManager = new MailManager(repo);
		mailManager.initialize();

		membership = mock(GangMembershipService.class);
		when(membership.join(anyInt(), any(), any())).thenReturn(
				new GangMembershipService.Result(true, null, Map.of()));

		MailMessages messages = mock(MailMessages.class);
		JavaPlugin   plugin   = mock(JavaPlugin.class);
		Argument     parent   = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");

		accept = new GangInviteAcceptCommand(plugin, new Tree<>(), parent, userManager, mock(MemberManager.class),
		                                     gangManager, mock(RankManager.class), mailManager, messages, membership);
		decline = new GangInviteDeclineCommand(plugin, new Tree<>(), parent, userManager,
		                                       mock(MemberManager.class), gangManager, mailManager, messages);
	}

	@AfterEach
	void tearDown() {
		settings.close();
		statics.close();
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

	/** The first pending invite from {@code senderGangId} in the order the manager lists them. */
	private MailItem firstFrom(int senderGangId) {
		return mailManager.findPendingForRecipient(uuid, MailType.GANG_INVITE).stream()
		                  .filter(mail -> mail.getSenderGangId() == senderGangId).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("accept <id>: joins the sender gang through its first pending invite and cancels every other invite")
	void accept_byId_takesTheFirstInviteFromThatGang() {
		MailItem fromTwo      = invite(2);
		MailItem fromThreeOne = invite(3);
		MailItem fromThreeTwo = invite(3);
		MailItem expected     = firstFrom(3);

		optionalChild(accept).executeArgument(player, new String[]{"gang", "accept", "3"});

		verify(membership).join(3, uuid, GangMembershipService.JoinSource.INVITE);
		for (MailItem mail : List.of(fromTwo, fromThreeOne, fromThreeTwo)) {
			assertEquals(mail == expected ? MailStatus.ACCEPTED : MailStatus.CANCELLED, mail.getStatus());
		}
	}

	@Test
	@DisplayName("accept <id>: an id with no pending invite from it touches nothing")
	void accept_byId_withoutInviteFromThatGang_touchesNothing() {
		MailItem fromTwo = invite(2);

		optionalChild(accept).executeArgument(player, new String[]{"gang", "accept", "3"});

		verify(membership, never()).join(anyInt(), any(), any());
		assertEquals(MailStatus.PENDING, fromTwo.getStatus());
		verify(user).sendMessage(Messages.GANG_INVITE_NO_INVITE_FROM.toString().replace("%gang%", "gang3"));
	}

	@Test
	@DisplayName("decline <id>: rejects only the first pending invite from that gang")
	void decline_byId_rejectsTheFirstInviteFromThatGang() {
		MailItem fromTwo      = invite(2);
		MailItem fromThreeOne = invite(3);
		MailItem fromThreeTwo = invite(3);
		MailItem expected     = firstFrom(3);

		optionalChild(decline).executeArgument(player, new String[]{"gang", "decline", "3"});

		for (MailItem mail : List.of(fromTwo, fromThreeOne, fromThreeTwo)) {
			assertEquals(mail == expected ? MailStatus.REJECTED : MailStatus.PENDING, mail.getStatus());
		}
	}

	@Test
	@DisplayName("decline <id>: an id with no pending invite from it touches nothing")
	void decline_byId_withoutInviteFromThatGang_touchesNothing() {
		MailItem fromTwo = invite(2);

		optionalChild(decline).executeArgument(player, new String[]{"gang", "decline", "3"});

		assertEquals(MailStatus.PENDING, fromTwo.getStatus());
		assertEquals(1, mailManager.findPendingForRecipient(uuid, MailType.GANG_INVITE).size());
	}

}
