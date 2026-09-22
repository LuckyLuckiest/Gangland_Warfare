package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.events.bounty.BountyEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangBountyEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.List;

/**
 * Moved out of gangland-impl's {@code BountyIncreaseListener} (WS5 G2 step 15b) — {@link GangBountyEvent} carries
 * a {@link Gang}, so impl can no longer name it; {@code onUserBountyIncrease} stayed behind, unchanged.
 */
@ListenerHandler
public class GangBountyMessageListener implements Listener {

	private final UserManager<Player> userManager;

	public GangBountyMessageListener(@Qualifier("online") UserManager<Player> userManager) {
		this.userManager = userManager;
	}

	@EventHandler
	public void onGangBountyIncrease(GangBountyEvent event) {
		Gang gang = event.getGang();

		if (gang == null || event.isCancelled()) return;

		List<User<Player>> onlineMembers = gang.getOnlineMembers(userManager::getUser);

		String bountyIncrement = getBountyIncrementMessage(event);

		onlineMembers.forEach(member -> member.getUser().sendMessage(bountyIncrement));
	}

	private String getBountyIncrementMessage(BountyEvent event) {
		String string = Messages.BOUNTY_INCREMENT.toString();
		String amount = Settings.formatAmount(event.getAmountApplied());
		return string.replace("%bounty%", amount);
	}

}
