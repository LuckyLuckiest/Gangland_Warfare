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

public class GangDatabase extends DatabaseHandler {

	private final JavaPlugin  plugin;
	private final FileManager fileManager;

	public GangDatabase(JavaPlugin plugin, FileManager fileManager) {
		super(plugin);
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

					for (String key : Objects.requireNonNull(section).getKeys(false))
						map.put(key.toLowerCase(), section.get(key));
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
		// For time use this method 'julianday('now')'
		getDatabase().createSchema("gang");
		getDatabase().table("data").createTable("id INT PRIMARY KEY NOT NULL", "name VARCHAR(16) NOT NULL",
		                                        "description TEXT NOT NULL", "members LONGTEXT NOT NULL",
		                                        "bounty DOUBLE NOT NULL", "alias LONGTEXT NOT NULL",
		                                        "created DATE NOT NULL");
		getDatabase().table("account").createTable("id INT PRIMARY KEY NOT NULL", "balance DOUBLE NOT NULL");
	}

}
