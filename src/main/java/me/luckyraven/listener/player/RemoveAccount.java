package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class RemoveAccount implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	public RemoveAccount(Gangland gangland) {
		this.gangland = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLeave(PlayerQuitEvent event) {
		User<Player> user = userManager.getUser(event.getPlayer());

		RepeatingTimer bountyTimer = user.getBounty().getRepeatingTimer();
		if (bountyTimer != null) bountyTimer.stop();
		// Remove the user from a user manager group
		userManager.remove(user);

		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			// must save user info
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof UserDatabase userDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						userDatabase.updateDataTable(user);
						userDatabase.updateBankTable(user);
					});
					break;
				}
		});

		if (user.getScoreboard() != null) {
			user.getScoreboard().end();
			user.setScoreboard(null);
		}
	}

}
