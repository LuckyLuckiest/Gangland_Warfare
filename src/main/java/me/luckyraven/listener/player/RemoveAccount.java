package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class RemoveAccount implements Listener {

	private static UserManager<Player> userManager = null;

	public RemoveAccount(Gangland gangland) {
		userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerLeave(PlayerQuitEvent event) {
		User<Player> user = userManager.getUser(event.getPlayer());

		// Remove the user from user manager group
		userManager.remove(user);
	}

}
