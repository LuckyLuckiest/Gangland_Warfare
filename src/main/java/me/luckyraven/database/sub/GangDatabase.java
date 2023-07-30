package me.luckyraven.database.sub;

import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.Member;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.file.FileManager;
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
		super(plugin);
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
		getDatabase().table("data").createTable("id INT PRIMARY KEY UNIQUE NOT NULL", "name CHAR(16) NOT NULL",
		                                        "display_name VARCHAR NOT NULL", "color VARCHAR NOT NULL",
		                                        "description TEXT NOT NULL", "balance DOUBLE NOT NULL",
		                                        "bounty DOUBLE NOT NULL", "alias LONGTEXT NOT NULL",
		                                        "created BIGINT NOT NULL");
		getDatabase().table("members").createTable("uuid CHAR(36) PRIMARY KEY UNIQUE NOT NULL",
		                                           "gang_id INT REFERENCES data (id)", "contribution DOUBLE NOT NULL",
		                                           "rank VARCHAR NOT NULL", "join_date BIGINT NOT NULL");
	}

	@Override
	public void insertInitialData() throws SQLException {

	}

	@Override
	public String getSchema() {
		return schema;
	}

	public void updateDataTable(Gang gang) throws SQLException {
		List<String> tempAlias = new ArrayList<>();

		for (Gang alias : gang.getAlly())
			tempAlias.add(String.valueOf(alias.getId()));

		String alias = getDatabase().createList(tempAlias);

		getDatabase().table("data").update("id = ?", new Object[]{gang.getId()}, new int[]{Types.INTEGER}, new String[]{
				"name", "display_name", "color", "description", "balance", "bounty", "alias", "created"
		}, new Object[]{
				gang.getName(), gang.getDisplayName(), gang.getColor(), gang.getDescription(), gang.getBalance(),
				gang.getBounty(), alias, gang.getCreated()
		}, new int[]{
				Types.CHAR, Types.VARCHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.DOUBLE, Types.DOUBLE,
				Types.LONGVARCHAR, Types.BIGINT
		});
	}

	public void updateMembersTable(Member member) throws SQLException {
		getDatabase().table("members").update("uuid = ?", new Object[]{member.getUuid()}, new int[]{Types.CHAR},
		                                      new String[]{"gang_id", "contribution", "rank", "join_date"},
		                                      new Object[]{
				                                      member.getGangId(), member.getContribution(),
				                                      member.getRank() == null ? null : member.getRank().getName(),
				                                      member.getGangJoinDate()
		                                      }, new int[]{Types.INTEGER, Types.DOUBLE, Types.VARCHAR, Types.BIGINT});
	}

}
