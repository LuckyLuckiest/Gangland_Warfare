package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.BankTable;
import me.luckyraven.database.tables.UserTable;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public final class RemoveAccount implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	public RemoveAccount(Gangland gangland) {
		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLeave(PlayerQuitEvent event) {
		User<Player> user = userManager.getUser(event.getPlayer());

		RepeatingTimer bountyTimer = user.getBounty().getRepeatingTimer();

		if (bountyTimer != null) bountyTimer.stop();

		// Remove the user from a user manager group
		userManager.remove(user);

		Initializer      initializer      = gangland.getInitializer();
		GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();
		DatabaseHelper   helper           = new DatabaseHelper(gangland, ganglandDatabase);
		List<Table<?>>   tables           = ganglandDatabase.getTables();

		UserTable userTable = initializer.getInstanceFromTables(UserTable.class, tables);
		BankTable bankTable = initializer.getInstanceFromTables(BankTable.class, tables);

		// must save user info
		helper.runQueriesAsync(database -> {
			userTable.updateTableQuery(database, user);
			bankTable.updateTableQuery(database, user);
		});

		if (user.getScoreboard() == null) return;

		user.getScoreboard().end();
		user.setScoreboard(null);
	}

}
