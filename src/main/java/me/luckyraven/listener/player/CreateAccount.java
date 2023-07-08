package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.util.UnhandledError;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;


public final class CreateAccount implements Listener {

	private static UserManager<Player> userManager = null;
	private final  Gangland            gangland;
	private final  DatabaseManager     databaseManager;

	public CreateAccount(Gangland gangland) {
		this.gangland = gangland;
		this.databaseManager = gangland.getInitializer().getDatabaseManager();
		userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = new User<>(player);

		for (DatabaseHandler handler : databaseManager.getDatabases())
			if (handler instanceof UserDatabase) {
				initializeUserData(user, new DatabaseHelper(gangland, handler));
				break;
			}

		// Add the user to user manager group
		userManager.add(user);
	}

	private void initializeUserData(User<Player> user, DatabaseHelper helper) {
		helper.runQueries(database -> {
			try {
				// <--------------- Account Info --------------->
				Database config  = database.table("account");
				String[] columns = config.getColumns().toArray(new String[0]);

				// check for account table
				Object[] accountInfo = config.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
				                                     new int[]{Types.CHAR}, new String[]{"*"});
				// create player data into database
				if (accountInfo.length == 0) {
					config.insert(columns, new Object[]{
							user.getUser().getUniqueId(), user.getBalance(), user.getGangId()
					}, new int[]{Types.CHAR, Types.DOUBLE, Types.INTEGER});
				}
				// use player data
				else {
					user.setBalance((double) accountInfo[1]);
					user.setGangId((int) accountInfo[2]);
				}

				// <--------------- Bank Info --------------->
				config = database.table("bank");
				columns = config.getColumns().toArray(new String[0]);

				// check for bank table
				Object[] bankInfo = config.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
				                                  new int[]{Types.CHAR}, new String[]{"*"});
				Bank bank = new Bank(user.getUser().getUniqueId(), user, "");
				// create player data into database
				if (bankInfo.length == 0) {
					config.insert(columns, new Object[]{
							user.getUser().getUniqueId(), bank.getName(), bank.getBalance()
					}, new int[]{Types.CHAR, Types.VARCHAR, Types.DOUBLE});
				}
				// use player data
				else {
					bank.setName(String.valueOf(bankInfo[1]));
					bank.setBalance((double) bankInfo[2]);
				}

				user.addAccount(bank);

				// <--------------- Data Info --------------->
				config = database.table("data");
				columns = config.getColumns().toArray(new String[0]);

				// check for data table
				Object[] dataInfo = config.select("uuid = ?", new Object[]{user.getUser().getUniqueId()},
				                                  new int[]{Types.CHAR}, new String[]{"*"});
				// create player data into database
				if (dataInfo.length == 0) {
					Date          joined        = new Date(user.getUser().getFirstPlayed());
					Instant       instant       = joined.toInstant();
					LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
					config.insert(columns, new Object[]{
							user.getUser().getUniqueId(), user.getKills(), user.getDeaths(), user.getMobKills(),
							user.hasBank(), user.getBounty(), localDateTime
					}, new int[]{
							Types.CHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.BOOLEAN, Types.DOUBLE,
							Types.DATE
					});
				}
				// use player data
				else {
					user.setKills((int) dataInfo[1]);
					user.setDeaths((int) dataInfo[2]);
					user.setMobKills((int) dataInfo[3]);
					user.setHasBank((boolean) dataInfo[4]);
					user.setBounty((double) dataInfo[5]);
				}
			} catch (SQLException exception) {
				gangland.getLogger().warning(UnhandledError.SQL_ERROR + ": " + exception.getMessage());

				exception.printStackTrace();
			}
		});
	}

}
