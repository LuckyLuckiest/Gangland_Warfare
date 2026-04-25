package me.luckyraven.listener.mail;

import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.mail.MailItem;
import me.luckyraven.mail.MailManager;
import me.luckyraven.mail.MailType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

/**
 * Surfaces pending mail to players as they join. Two short lines: one for gang invites still waiting on accept, one for
 * ally requests aimed at the player's gang. Players with no pending mail see nothing.
 */
@ListenerHandler
public final class MailJoinListener implements Listener {

	private final MailManager mailManager;

	public MailJoinListener(MailManager mailManager) {
		this.mailManager = mailManager;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		List<MailItem> invites = mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE);
		if (!invites.isEmpty()) {
			player.sendMessage(Messages.MAIL_PENDING_INVITES.toString()
			                                                .replace("%count%", String.valueOf(invites.size())));
		}
	}

}
