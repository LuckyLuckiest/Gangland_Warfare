package org.luckyraven.gangland.listener.player;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.events.gang.GangBountyEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.events.bounty.BountyEvent;
import org.luckyraven.gangland.gang.events.user.UserBountyEvent;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.List;
import java.util.Objects;

@ListenerHandler
public class BountyIncreaseListener implements Listener {

	private final UserManager<Player> userManager;

	public BountyIncreaseListener(@Qualifier("online") UserManager<Player> userManager) {
		this.userManager = userManager;
	}

	@EventHandler
	public void onUserBountyIncrease(UserBountyEvent event) {
		User<? extends OfflinePlayer> user = event.getUser();

		String bountyIncrement = getBountyIncrementMessage(event);

		if (!user.getUser().isOnline()) return;
		if (event.isCancelled()) return;

		Objects.requireNonNull(user.getUser().getPlayer()).sendMessage(bountyIncrement);
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
