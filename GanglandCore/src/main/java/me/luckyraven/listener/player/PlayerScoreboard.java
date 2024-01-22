package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.Scoreboard;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserDataInitEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerScoreboard implements Listener {

	private final Gangland gangland;

	public PlayerScoreboard(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onUserDataInitialize(UserDataInitEvent event) {
		User<Player> user = event.getUser();

		if (user.getScoreboard() != null) return;

		// create a scoreboard when the player joins
		user.setScoreboard(
				new Scoreboard(gangland.getInitializer().getScoreboardManager().getDriverHandler(event.getPlayer())));
		user.getScoreboard().start();
	}

}
