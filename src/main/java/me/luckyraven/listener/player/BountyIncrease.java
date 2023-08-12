package me.luckyraven.listener.player;

import me.luckyraven.bounty.BountyEvent;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class BountyIncrease implements Listener {

	@EventHandler
	public void onBountyIncrease(BountyEvent event) {
		User<Player> user = event.getUserBounty();

		if (user != null) if (!event.isCancelled()) {
			user.getUser().sendMessage(MessageAddon.BOUNTY_INCREMENT.toString()
			                                                        .replace("%bounty%", SettingAddon.formatDouble(
					                                                        event.getAmountApplied())));
		}
	}

}
