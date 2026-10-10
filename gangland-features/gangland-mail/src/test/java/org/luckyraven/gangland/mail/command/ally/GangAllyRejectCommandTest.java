package org.luckyraven.gangland.mail.command.ally;

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
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * T-202: {@code /glw gang ally reject} must not NPE when an online player has no member row; the online-gang notice
 * still reaches the members that do have one.
 */
@DisplayName("GangAllyRejectCommand - null-safe online member filter (T-202)")
class GangAllyRejectCommandTest {

	private static final int SENDER_GANG = 2;
	private static final int USER_GANG   = 1;

	private MockedStatic<Settings> settings;
	private BukkitStatics          statics;

	@BeforeEach
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
		settings = mockStatic(Settings.class, org.mockito.Mockito.CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		statics = BukkitStatics.install();
	}

	@AfterEach
	void tearDown() {
		settings.close();
		statics.close();
	}

	@Test
	@DisplayName("T-202: rejecting with an online player who has no member row does not throw and notifies the member")
	void reject_onlinePlayerWithoutMemberRow_noNpe() throws Exception {
		UUID   senderMemberId = UUID.randomUUID();
		UUID   orphanId       = UUID.randomUUID();
		UUID   userId         = UUID.randomUUID();

		Player orphan       = mock(Player.class);
		Player senderMember = mock(Player.class);
		Player user         = mock(Player.class);
		when(orphan.getUniqueId()).thenReturn(orphanId);
		when(senderMember.getUniqueId()).thenReturn(senderMemberId);
		when(user.getUniqueId()).thenReturn(userId);
		when(user.hasPermission(anyString())).thenReturn(true);
		statics.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(orphan, senderMember));

		Member senderRow = new Member(senderMemberId);
		senderRow.setGangId(SENDER_GANG);
		MemberManager memberManager = mock(MemberManager.class);
		when(memberManager.getMember(orphanId)).thenReturn(null);
		when(memberManager.getMember(senderMemberId)).thenReturn(senderRow);
		when(memberManager.getMember(userId)).thenReturn(new Member(userId));

		Gang userGang   = mock(Gang.class);
		Gang senderGang = mock(Gang.class);
		when(userGang.getId()).thenReturn(USER_GANG);
		when(userGang.getDisplayNameString()).thenReturn("gang1");
		when(senderGang.getId()).thenReturn(SENDER_GANG);
		when(senderGang.getDisplayNameString()).thenReturn("gang2");
		GangManager gangManager = mock(GangManager.class);
		when(gangManager.getGang(USER_GANG)).thenReturn(userGang);
		when(gangManager.getGang(SENDER_GANG)).thenReturn(senderGang);

		@SuppressWarnings("unchecked")
		User<Player> userRecord = mock(User.class);
		when(userRecord.hasGang()).thenReturn(true);
		when(userRecord.getGangId()).thenReturn(USER_GANG);
		@SuppressWarnings("unchecked")
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(user)).thenReturn(userRecord);

		FakeMailRepositoryContract repo        = new FakeMailRepositoryContract();
		MailManager                mailManager = new MailManager(repo);
		mailManager.initialize();
		MailItem request = new MailItem(mailManager.allocateId(), MailType.GANG_ALLY_REQUEST, null, SENDER_GANG, null,
		                                USER_GANG, null, System.currentTimeMillis(), 0L, MailStatus.PENDING, false);
		mailManager.send(request);

		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		GangAllyRejectCommand reject = new GangAllyRejectCommand(mock(JavaPlugin.class), new Tree<>(), parent,
		                                                         userManager, memberManager, gangManager, mailManager);

		reject.action().accept(reject, user, new String[]{"gang", "ally", "reject"});

		verify(senderMember).sendMessage(Messages.GANG_ALLY_REJECT.toString().replace("%gang%", "gang1"));
		assertEquals(MailStatus.REJECTED, request.getStatus());
		assertTrue(repo.deleted().contains(request));
	}

}
