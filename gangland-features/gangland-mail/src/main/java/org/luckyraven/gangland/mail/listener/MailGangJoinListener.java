package org.luckyraven.gangland.mail.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.gang.GangJoinEvent;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * T-265: a player who joins an open gang with {@code /glw gang join} loses every other pending gang invite, the same
 * as accepting one does (GR-23). Says so only if there were any.
 */
@ListenerHandler
public final class MailGangJoinListener implements Listener {

	private final MailManager  mailManager;
	private final MailMessages messages;

	public MailGangJoinListener(MailManager mailManager, MailMessages messages) {
		this.mailManager = mailManager;
		this.messages    = messages;
	}

	@EventHandler
	public void onGangJoin(GangJoinEvent event) {
		Player         player  = event.getPlayer();
		List<MailItem> pending = new ArrayList<>(mailManager.findPendingForRecipient(player.getUniqueId(),
		                                                                              MailType.GANG_INVITE));
		if (pending.isEmpty()) {
			return;
		}

		for (MailItem mail : pending) {
			mailManager.cancel(mail);
		}
		player.sendMessage(messages.inviteSiblingsCancelled());
	}

}
