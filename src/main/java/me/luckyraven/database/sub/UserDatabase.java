package me.luckyraven.database.sub;

import me.luckyraven.account.Account;
import me.luckyraven.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

public class UserDatabase extends DatabaseHandler {

	private final String schema;

	public UserDatabase(JavaPlugin plugin) {
		super(plugin);
		this.schema = "user";
	}

	@Override
	public Map<String, Object> credentials() {
		Map<String, Object> map = new HashMap<>();

		switch (getType()) {
			case DatabaseHandler.MYSQL -> {
				map.put("host", SettingAddon.getMysqlHost());
				map.put("password", SettingAddon.getMysqlPassword());
				map.put("port", SettingAddon.getMysqlPort());
				map.put("username", SettingAddon.getMysqlUsername());
			}
			case DatabaseHandler.SQLITE -> {
			}
			default -> throw new IllegalArgumentException("Unknown database type");
		}

		return map;
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
		                                        "deaths INT NOT NULL", "mob_kills INT NOT NULL",
		                                        "has_bank BOOLEAN NOT NULL", "bounty DOUBLE NOT NULL",
		                                        "date_joined DATE NOT NULL");
		getDatabase().table("bank").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "name TEXT NOT NULL",
		                                        "balance DOUBLE NOT NULL");
		getDatabase().table("account").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "balance DOUBLE NOT NULL",
		                                           "gang_id INT NOT NULL");
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
						"kills", "deaths", "mob_kills", "has_bank", "bounty", "date_joined"
				}, new Object[]{
						user.getKills(), user.getDeaths(), user.getMobKills(), user.hasBank(),
						user.getBounty().getAmount(), user.getUser().getFirstPlayed()
				}, new int[]{Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.BOOLEAN, Types.DOUBLE, Types.BIGINT});
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

	public void updateAccountTable(User<Player> user) throws SQLException {
		getDatabase().table("account").update("uuid = ?", new Object[]{user.getUser().getUniqueId()},
		                                      new int[]{Types.CHAR}, new String[]{"balance", "gang_id"},
		                                      new Object[]{user.getBalance(), user.getGangId()},
		                                      new int[]{Types.DOUBLE, Types.INTEGER});
	}

}
