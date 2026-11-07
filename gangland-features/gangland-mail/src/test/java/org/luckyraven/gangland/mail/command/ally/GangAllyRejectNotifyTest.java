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
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Characterises who is told what when an alliance request is rejected: the members of the sending gang hear the
 * rejecting gang's name, the members of the rejecting gang hear the sending gang's name, the sending gang is
 * notified first, and each audience is read from a fresh {@code Bukkit.getOnlinePlayers()} call. Players with no member
 * row are skipped. {@link GangAllyRejectCommandTest} only reaches the first audience.
 */
@DisplayName("GangAllyRejectCommand - who is notified of a rejection")
class GangAllyRejectNotifyTest {

	private static final int SENDER_GANG = 2;
	private static final int USER_GANG   = 1;

	private MockedStatic<Settings> settings;
	private BukkitStatics          statics;

	@BeforeEach
	void setUp() {
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path + " %gang%";
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

	private static Player online(UUID id) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		return player;
	}

	@Test
	@DisplayName("each gang's members are told the other gang's name, sending gang first, each from a fresh online read")
	void reject_notifiesBothGangsInOrder() {
		UUID senderMemberId = UUID.randomUUID();
		UUID senderOrphanId = UUID.randomUUID();
		UUID userMateId     = UUID.randomUUID();
		UUID strangerId     = UUID.randomUUID();
		UUID userId         = UUID.randomUUID();

		Player senderMember = online(senderMemberId);
		Player senderOrphan = online(senderOrphanId);
		Player userMate     = online(userMateId);
		Player stranger     = online(strangerId);
		Player user         = online(userId);
		when(user.hasPermission(anyString())).thenReturn(true);

		// the first online read only sees the sending gang's side, the second only the rejecting gang's: a swapped
		// notification order, or one shared read, would lose a message
		AtomicInteger reads = new AtomicInteger();
		statics.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> reads.incrementAndGet() == 1
				? List.of(senderOrphan, senderMember, stranger)
				: List.of(userMate, senderOrphan, stranger));

		MemberManager memberManager = mock(MemberManager.class);
		Member senderRow = new Member(senderMemberId);
		senderRow.setGangId(SENDER_GANG);
		Member mateRow = new Member(userMateId);
		mateRow.setGangId(USER_GANG);
		Member strangerRow = new Member(strangerId);
		strangerRow.setGangId(99);
		when(memberManager.getMember(senderMemberId)).thenReturn(senderRow);
		when(memberManager.getMember(userMateId)).thenReturn(mateRow);
		when(memberManager.getMember(strangerId)).thenReturn(strangerRow);
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

		MailManager mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();
		MailItem request = new MailItem(mailManager.allocateId(), MailType.GANG_ALLY_REQUEST, null, SENDER_GANG, null,
		                                USER_GANG, null, System.currentTimeMillis(), 0L, MailStatus.PENDING, false);
		mailManager.send(request);

		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		GangAllyRejectCommand reject = new GangAllyRejectCommand(mock(JavaPlugin.class), new Tree<>(), parent,
		                                                         userManager, memberManager, gangManager, mailManager);

		reject.action().accept(reject, user, new String[]{"gang", "ally", "reject"});

		String toSenders = Messages.GANG_ALLY_REJECT.toString().replace("%gang%", "gang1");
		String toRejecters = Messages.GANG_ALLY_REJECT.toString().replace("%gang%", "gang2");
		InOrder order = inOrder(senderMember, userMate);
		order.verify(senderMember).sendMessage(toSenders);
		order.verify(userMate).sendMessage(toRejecters);
		verify(senderMember, times(1)).sendMessage(anyString());
		verify(userMate, times(1)).sendMessage(anyString());
		verify(senderOrphan, never()).sendMessage(anyString());
		verify(stranger, never()).sendMessage(anyString());
		assertEquals(2, reads.get());
		assertEquals(MailStatus.REJECTED, request.getStatus());
	}

}
