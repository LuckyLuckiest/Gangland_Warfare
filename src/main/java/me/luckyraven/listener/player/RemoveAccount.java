package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.timer.RepeatingTimer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class RemoveAccount implements Listener {

	private final UserManager<Player> userManager;

	public RemoveAccount(Gangland gangland) {
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerLeave(PlayerQuitEvent event) {
		User<Player> user = userManager.getUser(event.getPlayer());

		RepeatingTimer bountyTimer = user.getBounty().getRepeatingTimer();
		if (bountyTimer != null) bountyTimer.stop();
		// Remove the user from user manager group
		userManager.remove(user);
	}

}
