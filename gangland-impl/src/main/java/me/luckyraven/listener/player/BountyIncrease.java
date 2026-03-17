package me.luckyraven.listener.player;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.events.bounty.BountyEvent;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.user.User;
import me.luckyraven.events.gang.GangBountyEvent;
import me.luckyraven.events.user.UserBountyEvent;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Objects;

@ListenerHandler
@RequiredArgsConstructor
public class BountyIncrease implements Listener {

	private final Gangland gangland;

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

		List<User<Player>> onlineMembers = gang.getOnlineMembers(gangland.getInitializer().getUserManager());

		String bountyIncrement = getBountyIncrementMessage(event);

		onlineMembers.forEach(member -> member.getUser().sendMessage(bountyIncrement));
	}

	private String getBountyIncrementMessage(BountyEvent event) {
		String string = Messages.BOUNTY_INCREMENT.toString();
		String amount = SettingAddon.formatDouble(event.getAmountApplied());
		return string.replace("%bounty%", amount);
	}

}
