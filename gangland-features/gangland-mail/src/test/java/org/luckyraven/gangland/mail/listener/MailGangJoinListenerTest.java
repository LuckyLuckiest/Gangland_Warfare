package org.luckyraven.gangland.mail.listener;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.gang.GangJoinEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("MailGangJoinListener - joining a gang drops the player's other pending invites (T-265)")
class MailGangJoinListenerTest {

	@Test
	@DisplayName("a join cancels every other pending gang invite and tells the player so")
	void join_cancelsEveryPendingInviteAndSaysSo() {
		MailManager mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();
		MailMessages messages = mock(MailMessages.class);
		when(messages.inviteSiblingsCancelled()).thenReturn("siblings cancelled");

		UUID   playerId = UUID.randomUUID();
		Player player   = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);

		MailItem vipers = send(mailManager, MailType.GANG_INVITE, 5, playerId);
		MailItem kings  = send(mailManager, MailType.GANG_INVITE, 6, playerId);

		Gang joined = mock(Gang.class);
		new MailGangJoinListener(mailManager, messages).onGangJoin(new GangJoinEvent(joined, player));

		assertTrue(mailManager.findPendingForRecipient(playerId, MailType.GANG_INVITE).isEmpty());
		assertEquals(MailStatus.CANCELLED, vipers.getStatus());
		assertEquals(MailStatus.CANCELLED, kings.getStatus());
		verify(player).sendMessage("siblings cancelled");
	}

	@Test
	@DisplayName("a join with no pending invites says nothing")
	void join_withoutInvites_saysNothing() {
		MailManager mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();
		MailMessages messages = mock(MailMessages.class);
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		new MailGangJoinListener(mailManager, messages).onGangJoin(new GangJoinEvent(mock(Gang.class), player));

		verify(player, never()).sendMessage(anyString());
	}

	private static MailItem send(MailManager mailManager, MailType type, int senderGang, UUID recipient) {
		long now = System.currentTimeMillis();
		MailItem mail = new MailItem(mailManager.allocateId(), type, null, senderGang, recipient, MailItem.NO_GANG, null,
		                             now, 0, MailStatus.PENDING, false);
		mailManager.send(mail);
		return mail;
	}

}
