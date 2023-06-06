package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;


public final class CreateAccount implements Listener {

	private static UserManager<Player> userManager = null;

	public CreateAccount(Gangland gangland) {
		userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = new User<>(player);

		// Add the user to user manager group
		userManager.add(user);

		// TODO get player data
	}

}
