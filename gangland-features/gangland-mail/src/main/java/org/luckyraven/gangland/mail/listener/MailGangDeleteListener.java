package org.luckyraven.gangland.mail.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Drops every pending invite and alliance request sent by or addressed to a disbanded gang, so none keeps pointing
 * at a gang that no longer exists.
 */
@ListenerHandler
public final class MailGangDeleteListener implements Listener {

	private final MailManager mailManager;

	public MailGangDeleteListener(MailManager mailManager) {
		this.mailManager = mailManager;
	}

	@EventHandler
	public void onGangDelete(GangDeleteEvent event) {
		mailManager.cancelForGang(event.getGang().getId());
	}

}
