package org.luckyraven.gangland.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.bean.listener.ListenerPriority;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.scoreboard.Scoreboard;
import org.luckyraven.gangland.scoreboard.ScoreboardManager;
import org.luckyraven.gangland.scoreboard.driver.DriverHandler;

@ListenerHandler(condition = "isScoreboardEnabled",
                 priority = ListenerPriority.LOW)
public class PlayerScoreboardListener implements Listener {

	private final Gangland          gangland;
	private final ScoreboardManager scoreboardManager;

	public PlayerScoreboardListener(Gangland gangland, ScoreboardManager scoreboardManager) {
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
