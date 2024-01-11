package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.user.User;
import me.luckyraven.feature.bounty.BountyEvent;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Objects;

public class BountyIncrease implements Listener {

	private final Gangland gangland;

	public BountyIncrease(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onBountyIncrease(BountyEvent event) {
		User<? extends OfflinePlayer> user = event.getUserBounty();

		String bountyIncrement = MessageAddon.BOUNTY_INCREMENT.toString()
		                                                      .replace("%bounty%", SettingAddon.formatDouble(
				                                                      event.getAmountApplied()));

		if (user != null && user.getUser().isOnline())
			if (!event.isCancelled()) Objects.requireNonNull(user.getUser().getPlayer()).sendMessage(bountyIncrement);

		Gang gang = event.getGangBounty();
		if (gang != null) if (!event.isCancelled()) {
			for (User<Player> member : gang.getOnlineMembers(gangland.getInitializer().getUserManager()))
				member.getUser().sendMessage(bountyIncrement);
		}
	}

}
