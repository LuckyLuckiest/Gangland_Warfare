package me.luckyraven.listener.player;

import me.luckyraven.copsncrooks.events.bounty.BountyEvent;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.events.gang.GangBountyEvent;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.events.user.UserBountyEvent;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

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
