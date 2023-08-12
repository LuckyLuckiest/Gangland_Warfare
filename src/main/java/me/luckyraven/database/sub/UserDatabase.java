package me.luckyraven.database.sub;

import me.luckyraven.account.Account;
import me.luckyraven.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

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
		                                        "bounty DOUBLE NOT NULL", "experience DOUBLE NOT NULL",
		                                        "date_joined BIGINT NOT NULL");
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

	public void insertDataTable(User<Player> user) throws SQLException {
		Database      config          = getDatabase().table("data");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.subList(1, columnsTemp.size()).toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			dataTypes[i] = columnsDataType.get(i);

		config.insert(columns, new Object[]{
				user.getUser().getUniqueId(), user.getKills(), user.getDeaths(), user.getMobKills(), user.getGangId(),
				user.hasBank(), user.getBalance(), user.getBounty().getAmount(), user.getLevel().getExperience(),
				user.getUser().getFirstPlayed()
		}, dataTypes);
	}

	public void updateDataTable(User<Player> user) throws SQLException {
		Database      config          = getDatabase().table("data");
		List<String>  columnsTemp     = config.getColumns();
		String[]      columns         = columnsTemp.subList(1, columnsTemp.size()).toArray(String[]::new);
		List<Integer> columnsDataType = config.getColumnsDataType(columns);

		int[] dataTypes = new int[columnsDataType.size()];
		for (int i = 0; i < dataTypes.length; i++)
			dataTypes[i] = columnsDataType.get(i);

		config.update("uuid = ?", new Object[]{user.getUser().getUniqueId()}, new int[]{Types.CHAR}, columns,
		              new Object[]{
				              user.getKills(), user.getDeaths(), user.getMobKills(), user.getGangId(), user.hasBank(),
				              user.getBalance(), user.getBounty().getAmount(), user.getLevel().getExperience(),
				              user.getUser().getFirstPlayed()
		              }, dataTypes);
	}

	public void insertBankTable(User<Player> user) throws SQLException {
		for (Account<?, ?> account : user.getLinkedAccounts())
			if (account instanceof Bank bank) {
				Database      config          = getDatabase().table("bank");
				List<String>  columnsTemp     = config.getColumns();
				String[]      columns         = columnsTemp.subList(1, columnsTemp.size()).toArray(String[]::new);
				List<Integer> columnsDataType = config.getColumnsDataType(columns);

				int[] dataTypes = new int[columnsDataType.size()];
				for (int i = 0; i < dataTypes.length; i++)
					dataTypes[i] = columnsDataType.get(i);

				config.insert(columns, new Object[]{user.getUser().getUniqueId(), bank.getName(), bank.getBalance()},
				              dataTypes);
				break;
			}
	}

	public void updateBankTable(User<Player> user) throws SQLException {
		for (Account<?, ?> account : user.getLinkedAccounts())
			if (account instanceof Bank bank) {
				Database      config          = getDatabase().table("bank");
				List<String>  columnsTemp     = config.getColumns();
				String[]      columns         = columnsTemp.subList(1, columnsTemp.size()).toArray(String[]::new);
				List<Integer> columnsDataType = config.getColumnsDataType(columns);

				int[] dataTypes = new int[columnsDataType.size()];
				for (int i = 0; i < dataTypes.length; i++)
					dataTypes[i] = columnsDataType.get(i);

				getDatabase().table("bank").update("uuid = ?", new Object[]{user.getUser().getUniqueId()},
				                                   new int[]{Types.CHAR}, columns,
				                                   new Object[]{bank.getName(), bank.getBalance()},
				                                   dataTypes);
				break;
			}
	}

}
