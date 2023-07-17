package me.luckyraven.database.sub;

import me.luckyraven.account.gang.Gang;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.rank.Rank;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

public class GangDatabase extends DatabaseHandler {

	private final JavaPlugin  plugin;
	private final FileManager fileManager;
	private       String      schema;

	public GangDatabase(JavaPlugin plugin, FileManager fileManager) {
		super(plugin, fileManager);
		this.plugin = plugin;
		this.fileManager = fileManager;
		this.schema = "gang";
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
		getDatabase().table("data").createTable("id INT PRIMARY KEY NOT NULL", "name CHAR(16) NOT NULL",
		                                        "description TEXT NOT NULL", "members LONGTEXT NOT NULL",
		                                        "contribution LONGTEXT NOT NULL", "bounty DOUBLE NOT NULL",
		                                        "alias LONGTEXT NOT NULL", "created BIGINT NOT NULL");
		getDatabase().table("account").createTable("id INT PRIMARY KEY NOT NULL", "balance DOUBLE NOT NULL");
	}

	@Override
	public void insertInitialData() throws SQLException {

	}

	@Override
	public String getSchema() {
		return schema;
	}

	public void updateDataTable(Gang gang) throws SQLException {
		List<String> tempMembers = new ArrayList<>();

		for (Map.Entry<UUID, Rank> entry : gang.getGroup().entrySet())
			tempMembers.add(entry.getKey() + ":" + entry.getValue().getName());

		String members = getDatabase().createList(tempMembers);

		List<String> tempContributions = new ArrayList<>();

		for (Map.Entry<UUID, Double> entry : gang.getContribution().entrySet())
			tempContributions.add(entry.getKey() + ":" + entry.getValue());

		String contributions = getDatabase().createList(tempContributions);

		List<String> tempAlias = new ArrayList<>();

		for (Gang alias : gang.getAlias())
			tempAlias.add(String.valueOf(alias.getId()));

		String alias = getDatabase().createList(tempAlias);

		getDatabase().table("data").update("id = ?", new Object[]{gang.getId()}, new int[]{Types.INTEGER}, new String[]{
				"name", "description", "members", "contribution", "bounty", "alias", "created"
		}, new Object[]{
				gang.getName(), gang.getDescription(), members, contributions, gang.getBounty(), alias,
				gang.getCreated()
		}, new int[]{
				Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.LONGVARCHAR, Types.DOUBLE, Types.LONGVARCHAR,
				Types.BIGINT
		});
	}

	public void updateAccountTable(Gang gang) throws SQLException {
		getDatabase().table("account").update("id = ?", new Object[]{gang.getId()}, new int[]{Types.INTEGER},
		                                      new String[]{"balance"}, new Object[]{gang.getBalance()},
		                                      new int[]{Types.DOUBLE});
	}

}
