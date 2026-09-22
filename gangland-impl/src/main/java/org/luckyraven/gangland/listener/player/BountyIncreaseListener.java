package org.luckyraven.gangland.listener.player;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.events.bounty.BountyEvent;
import org.luckyraven.gangland.core.events.user.UserBountyEvent;
import org.luckyraven.gangland.core.user.User;

import java.util.Objects;

/**
 * {@code onGangBountyIncrease} moved to the gang module's {@code GangBountyMessageListener} (WS5 G2 step 15b) —
 * {@code GangBountyEvent} is module-owned (it carries a {@code Gang}), so impl can no longer name it.
 */
@ListenerHandler
public class BountyIncreaseListener implements Listener {

	@EventHandler
	public void onUserBountyIncrease(UserBountyEvent event) {
		User<? extends OfflinePlayer> user = event.getUser();

		String bountyIncrement = getBountyIncrementMessage(event);

		if (!user.getUser().isOnline()) return;
		if (event.isCancelled()) return;

		Objects.requireNonNull(user.getUser().getPlayer()).sendMessage(bountyIncrement);
	}

	private String getBountyIncrementMessage(BountyEvent event) {
		String string = Messages.BOUNTY_INCREMENT.toString();
		String amount = Settings.formatAmount(event.getAmountApplied());
		return string.replace("%bounty%", amount);
	}

}
