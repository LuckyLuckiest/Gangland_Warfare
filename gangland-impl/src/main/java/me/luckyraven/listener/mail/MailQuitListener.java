package me.luckyraven.listener.mail;

import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.Member;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.mail.MailItem;
import me.luckyraven.mail.MailManager;
import me.luckyraven.mail.MailType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Pauses pending {@link MailType#GANG_ALLY_REQUEST} mails addressed to the quitter's gang once the quitter is the last
 * online member of that gang. Paired with {@link MailJoinListener}, which resumes the countdown when any member
 * returns. Without this pause, requests sent while the gang is around would expire silently after the gang fully logs
 * off, before anyone could act on them.
 */
@ListenerHandler
public final class MailQuitListener implements Listener {

	private final MailManager   mailManager;
	private final GangManager   gangManager;
	private final MemberManager memberManager;

	public MailQuitListener(MailManager mailManager, GangManager gangManager, MemberManager memberManager) {
		this.mailManager   = mailManager;
		this.gangManager   = gangManager;
		this.memberManager = memberManager;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		Member member = memberManager.getMember(player.getUniqueId());
		if (member == null || member.getGangId() == MailItem.NO_GANG) return;

		Gang gang = gangManager.getGang(member.getGangId());
		if (gang == null) return;

		// PlayerQuitEvent fires before Bukkit removes the player from getOnlinePlayers(), so exclude this UUID when
		// checking whether anyone else from the gang is still around.
		if (gang.hasAnyMemberOnlineExcluding(player.getUniqueId())) return;

		List<MailItem> requests = mailManager.findPendingForRecipientGang(gang.getId(), MailType.GANG_ALLY_REQUEST);
		if (requests.isEmpty()) return;

		long now = System.currentTimeMillis();
		for (MailItem request : requests) {
			if (request.isPaused()) continue;
			request.setPausedAt(now);
		}
	}

}
