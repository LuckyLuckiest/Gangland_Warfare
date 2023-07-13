package me.luckyraven.database.sub;

import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.datastructure.Node;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RankDatabase extends DatabaseHandler {

	private final JavaPlugin plugin;
	private final FileManager fileManager;
	private       String schema;

	public RankDatabase(JavaPlugin plugin, FileManager fileManager) {
		super(plugin, fileManager);
		this.plugin = plugin;
		this.fileManager = fileManager;
		this.schema = "rank";
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
					plugin.getLogger().warning(
							UnhandledError.FILE_LOADER_ERROR.getMessage() + ": " + exception.getMessage());
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
		getDatabase().table("data").createTable("id INT PRIMARY KEY NOT NULL", "name TEXT NOT NULL",
		                                        "permissions LONGTEXT NOT NULL", "parent LONGTEXT");
	}

	@Override
	public void insertInitialData() throws SQLException {
		Database dataTable = getDatabase().table("data");

		String head = SettingAddon.getGangRankHead(), tail = SettingAddon.getGangRankTail();

		Object[] tailRow = dataTable.select("name = ?", new Object[]{tail}, new int[]{Types.VARCHAR},
		                                    new String[]{"*"});
		int rows = dataTable.totalRows() + 1;
		if (tailRow.length == 0) dataTable.insert(new String[]{"id", "name", "permissions", "parent"},
		                                          new Object[]{rows, tail, "", ""}, new int[]{
						Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR
				});

		Object[] headRow = dataTable.select("name = ?", new Object[]{head}, new int[]{Types.VARCHAR},
		                                    new String[]{"*"});
		if (headRow.length == 0) dataTable.insert(new String[]{"id", "name", "permissions", "parent"},
		                                          new Object[]{rows + 1, head, "", tail}, new int[]{
						Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR
				});
	}

	@Override
	public String getSchema() {
		return schema;
	}

	public void updateDataTable(Rank rank) throws SQLException {
		String permissions = getDatabase().createList(rank.getPermissions());
		String children = getDatabase().createList(rank.getNode()
		                                               .getChildren()
		                                               .stream()
		                                               .map(Node::getData)
		                                               .map(Rank::getName)
		                                               .toList());
		getDatabase().table("data").update("id = ?", new Object[]{rank.getUsedId()}, new int[]{Types.INTEGER},
		                                   new String[]{"name", "permissions", "parent"},
		                                   new Object[]{rank.getName(), permissions, children},
		                                   new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR});
	}

}
