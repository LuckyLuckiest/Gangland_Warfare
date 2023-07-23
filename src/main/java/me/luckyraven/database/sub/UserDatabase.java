package me.luckyraven.database.sub;

import me.luckyraven.account.Account;
import me.luckyraven.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UserDatabase extends DatabaseHandler {

	private final JavaPlugin  plugin;
	private final FileManager fileManager;
	private       String      schema;

	public UserDatabase(JavaPlugin plugin, FileManager fileManager) {
		super(plugin, fileManager);
		this.plugin = plugin;
		this.fileManager = fileManager;
		this.schema = "user";
	}

	@Override
	public Map<String, Object> credentials() {
		Map<String, Object> map = new HashMap<>();

		switch (getType()) {
			case DatabaseHandler.MYSQL -> {
				try {
					fileManager.checkFileLoaded("settings");

					FileConfiguration settings = fileManager.getFile("settings").getFileConfiguration();

					ConfigurationSection section = settings.getConfigurationSection("Database.MySQL");

					for (String key : Objects.requireNonNull(section).getKeys(false))
						map.put(key.toLowerCase(), section.get(key));
				} catch (IOException exception) {
					plugin.getLogger().warning(UnhandledError.FILE_LOADER_ERROR + ": " + exception.getMessage());
				}
			}
			case DatabaseHandler.SQLITE -> this.schema = "database\\" + this.schema;
			default -> throw new IllegalArgumentException("Unknown database type");
		}

		return map;
	}

	@Override
	public void createSchema() throws SQLException, IOException {
		getDatabase().createSchema(schema);

		// Switch the schema only when using mysql, because it needs to create the schema from the connection
		// then change the jdbc url to the new database
		if (getType() == MYSQL) getDatabase().switchSchema(schema);
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
		return schema;
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
