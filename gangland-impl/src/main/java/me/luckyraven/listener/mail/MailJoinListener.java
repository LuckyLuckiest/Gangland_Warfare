package me.luckyraven.listener.mail;

import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
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
 * Surfaces pending mail to players as they join. Gang invites are listed by inviting gang so the recipient can pick the
 * right one with {@code /glw gang accept <gang>}; players with no pending mail see nothing.
 */
@ListenerHandler
public final class MailJoinListener implements Listener {

	private final MailManager mailManager;
	private final GangManager gangManager;

	public MailJoinListener(MailManager mailManager, GangManager gangManager) {
		this.mailManager = mailManager;
		this.gangManager = gangManager;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		List<MailItem> invites = mailManager.findPendingForRecipient(player.getUniqueId(), MailType.GANG_INVITE);
		if (invites.isEmpty()) return;

		player.sendMessage(Messages.MAIL_PENDING_INVITES.toString()
		                                                .replace("%count%", String.valueOf(invites.size())));

		for (MailItem invite : invites) {
			Gang gang = gangManager.getGang(invite.getSenderGangId());
			if (gang == null) continue;

			player.sendMessage(Messages.MAIL_PENDING_INVITES_ENTRY.toString()
			                                                      .replace("%gang%", gang.getDisplayNameString()));
		}
	}

}
