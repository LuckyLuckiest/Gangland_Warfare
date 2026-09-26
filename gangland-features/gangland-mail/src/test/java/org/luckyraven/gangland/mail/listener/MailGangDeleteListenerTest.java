package org.luckyraven.gangland.mail.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MailGangDeleteListener - a disbanded gang leaves no pending mail behind")
class MailGangDeleteListenerTest {

	@Test
	@DisplayName("invites and ally requests sent by or to the deleted gang are dropped; other gangs' mail stays")
	void gangDelete_dropsEveryPendingMailOfThatGang() {
		MailManager mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();

		MailItem invite   = send(mailManager, MailType.GANG_INVITE, 5, UUID.randomUUID(), MailItem.NO_GANG);
		MailItem incoming = send(mailManager, MailType.GANG_ALLY_REQUEST, 6, null, 5);
		MailItem outgoing = send(mailManager, MailType.GANG_ALLY_REQUEST, 5, null, 7);
		MailItem other    = send(mailManager, MailType.GANG_ALLY_REQUEST, 6, null, 7);

		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(5);
		new MailGangDeleteListener(mailManager).onGangDelete(new GangDeleteEvent(gang));

		assertEquals(List.of(other), List.copyOf(mailManager.getAll()));
		assertEquals(MailStatus.CANCELLED, invite.getStatus());
		assertEquals(MailStatus.CANCELLED, incoming.getStatus());
		assertEquals(MailStatus.CANCELLED, outgoing.getStatus());
	}

	private static MailItem send(MailManager mailManager, MailType type, int senderGang, UUID recipient,
	                             int recipientGang) {
		long now = System.currentTimeMillis();
		MailItem mail = new MailItem(mailManager.allocateId(), type, null, senderGang, recipient, recipientGang, null,
		                             now, 0, MailStatus.PENDING, false);
		mailManager.send(mail);
		return mail;
	}

}
