package org.luckyraven.gangland.listener.mail;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailType;

import java.util.List;

/**
 * Surfaces pending mail to players as they join. Gang invites are listed by inviting gang so the recipient can pick the
 * right one with {@code /glw gang accept <gang>}; pending ally requests addressed to the joiner's gang are likewise
 * surfaced and have their expiry countdown resumed (the request is paused while the recipient gang has nobody online).
 * Players with no pending mail see nothing.
 */
@ListenerHandler
public final class MailJoinListener implements Listener {

	private final MailManager   mailManager;
	private final GangManager   gangManager;
	private final MemberManager memberManager;

	public MailJoinListener(MailManager mailManager, GangManager gangManager, MemberManager memberManager) {
		this.mailManager   = mailManager;
		this.gangManager   = gangManager;
		this.memberManager = memberManager;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		surfaceInvites(player);
		resumeAndSurfaceAllyRequests(player);
	}

	private void surfaceInvites(Player player) {
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

	private void resumeAndSurfaceAllyRequests(Player player) {
		Member member = memberManager.getMember(player.getUniqueId());
		if (member == null || member.getGangId() == MailItem.NO_GANG) return;

		List<MailItem> requests = mailManager.findPendingForRecipientGang(member.getGangId(),
		                                                                  MailType.GANG_ALLY_REQUEST);
		if (requests.isEmpty()) return;

		long now = System.currentTimeMillis();
		for (MailItem request : requests) {
			if (!request.isPaused()) continue;
			request.setExpiresAt(request.getExpiresAt() + (now - request.getPausedAt()));
			request.setPausedAt(0L);
		}

		player.sendMessage(Messages.MAIL_PENDING_ALLY_REQUESTS.toString()
		                                                      .replace("%count%", String.valueOf(requests.size())));

		for (MailItem request : requests) {
			Gang sender = gangManager.getGang(request.getSenderGangId());
			if (sender == null) continue;

			player.sendMessage(Messages.MAIL_PENDING_ALLY_REQUESTS_ENTRY.toString()
			                                                            .replace("%gang%",
			                                                                     sender.getDisplayNameString()));
		}
	}

}
