package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.user.User;
import me.luckyraven.events.user.UserDataInitEvent;
import me.luckyraven.scoreboard.Scoreboard;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.driver.DriverHandler;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.listener.ListenerPriority;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@ListenerHandler(condition = "isScoreboardEnabled",
				 priority = ListenerPriority.LOW)
public class PlayerScoreboard implements Listener {

	private final Gangland          gangland;
	private final ScoreboardManager scoreboardManager;

	public PlayerScoreboard(Gangland gangland, ScoreboardManager scoreboardManager) {
		this.gangland          = gangland;
		this.scoreboardManager = scoreboardManager;
	}

	@EventHandler
	public void onUserDataInitialize(UserDataInitEvent event) {
		User<Player> user = event.getUser();

		if (user.getScoreboard() != null) return;

		// create a scoreboard when the player joins
		DriverHandler driverHandler = scoreboardManager.getDriverHandler(event.getPlayer());
		Scoreboard    scoreboard    = new Scoreboard(gangland, driverHandler);

		user.setScoreboard(scoreboard);
		user.getScoreboard().start();
	}

}
