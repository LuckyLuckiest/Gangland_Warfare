package me.luckyraven.database.sub;

import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UserDatabase extends DatabaseHandler {

	private final JavaPlugin  plugin;
	private final FileManager fileManager;

	public UserDatabase(JavaPlugin plugin, int type, FileManager fileManager) {
		super(plugin, type, fileManager);
		this.plugin = plugin;
		this.fileManager = fileManager;
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

					for (String key : Objects.requireNonNull(section).getKeys(false)) map.put(key, section.get(key));
				} catch (IOException exception) {
					plugin.getLogger().warning(
							UnhandledError.FILE_LOADER_ERROR.getMessage() + ": " + exception.getMessage());
				}
			}
			case DatabaseHandler.SQLITE -> {

			}
			default -> throw new IllegalArgumentException("Unknown database type");
		}

		return map;
	}

	@Override
	public void tables() throws SQLException {
		getDatabase().createSchema("user");
		getDatabase().table("data").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "kills INT NOT NULL",
		                                        "deaths INT NOT NULL", "mob_kills INT NOT NULL",
		                                        "has_bank BOOLEAN NOT NULL", "has_gang BOOLEAN NOT NULL",
		                                        "bounty DOUBLE NOT NULL", "date_joined DATE NOT NULL");
		getDatabase().table("bank").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "name TEXT NOT NULL",
		                                        "balance DOUBLE NOT NULL");
		getDatabase().table("account").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "balance DOUBLE NOT NULL",
		                                           "gang_id INT NOT NULL");
	}

}
