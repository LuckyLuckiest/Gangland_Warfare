package me.luckyraven.database.sub;

import me.luckyraven.account.Account;
import me.luckyraven.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.database.DatabaseHandler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;

public class UserDatabase extends DatabaseHandler {

	private final String schema;

	public UserDatabase(JavaPlugin plugin) {
		super(plugin);
		this.schema = "user";
	}

	@Override
	public void createSchema() throws SQLException, IOException {
		getDatabase().createSchema(getSchema());

		// Switch the schema only when using mysql, because it needs to create the schema from the connection
		// then change the jdbc url to the new database
		if (getType() == MYSQL) getDatabase().switchSchema(getSchema());
	}

	@Override
	public void createTables() throws SQLException {
		getDatabase().table("data").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "kills INT NOT NULL",
		                                        "deaths INT NOT NULL", "mob_kills INT NOT NULL", "gang_id INT NOT NULL",
		                                        "has_bank BOOLEAN NOT NULL", "balance DOUBLE NOT NULL",
		                                        "bounty DOUBLE NOT NULL", "level DOUBLE NOT NULL",
		                                        "date_joined DATE NOT NULL");
		getDatabase().table("bank").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "name TEXT NOT NULL",
		                                        "balance DOUBLE NOT NULL");
	}

	@Override
	public void insertInitialData() throws SQLException {

	}

	@Override
	public String getSchema() {
		return switch (getType()) {
			case DatabaseHandler.MYSQL -> schema;
			case DatabaseHandler.SQLITE -> "database\\" + this.schema;
			default -> null;
		};
	}

	public void updateDataTable(User<Player> user) throws SQLException {
		getDatabase().table("data").update("uuid = ?", new Object[]{user.getUser().getUniqueId()},
		                                   new int[]{Types.CHAR}, new String[]{
						"kills", "deaths", "mob_kills", "gang_id", "has_bank", "balance", "bounty", "level",
						"date_joined"
				}, new Object[]{
						user.getKills(), user.getDeaths(), user.getMobKills(), user.getGangId(), user.hasBank(),
						user.getBalance(), user.getBounty().getAmount(), user.getLevel().getAmount(),
						user.getUser().getFirstPlayed()
				}, new int[]{
						Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.BOOLEAN, Types.DOUBLE,
						Types.DOUBLE, Types.DOUBLE, Types.BIGINT
				});
	}

	public void updateBankTable(User<Player> user) throws SQLException {
		for (Account<?, ?> account : user.getLinkedAccounts())
			if (account instanceof Bank bank) {
				getDatabase().table("bank").update("uuid = ?", new Object[]{user.getUser().getUniqueId()},
				                                   new int[]{Types.CHAR}, new String[]{"name", "balance"},
				                                   new Object[]{bank.getName(), bank.getBalance()},
				                                   new int[]{Types.VARCHAR, Types.DOUBLE});
				break;
			}
	}

}
