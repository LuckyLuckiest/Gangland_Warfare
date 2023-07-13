package me.luckyraven.database;

import me.luckyraven.file.FileManager;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

	private final List<DatabaseHandler> databases;
	private final JavaPlugin            plugin;

	public DatabaseManager(JavaPlugin plugin) {
		this.plugin = plugin;
		this.databases = new ArrayList<>();
	}

	public void addDatabase(DatabaseHandler database) {
		databases.add(database);
	}

	public void initializeDatabases() {
		for (DatabaseHandler database : databases) database.initialize();
	}

	public void startBackup(JavaPlugin plugin, FileManager fileManager, String db) {
		try {
			// tightly coupled to settings
			fileManager.checkFileLoaded("settings");

			FileConfiguration settings = fileManager.getFile("settings").getFileConfiguration();

			if (settings.getBoolean("Database.SQLite.Backup")) {
				for (DatabaseHandler handler : databases) {
					switch (handler.getType()) {
						case DatabaseHandler.MYSQL -> {
							// TODO create a backup from mysql to sqlite
						}
						case DatabaseHandler.SQLITE -> {
							// TODO create a backup from sqlite to mysql
						}
					}
				}

				plugin.getLogger().info(String.format("Backup done for '%s' database", db));
			}

		} catch (IOException exception) {
			plugin.getLogger().warning(UnhandledError.FILE_LOADER_ERROR + ": " + exception.getMessage());
		}
	}

	public void closeConnections(FileManager fileManager) {
		for (DatabaseHandler databaseHandler : databases) {
			Database database = databaseHandler.getDatabase();
			if (database != null && database.getConnection() != null) {
				startBackup(plugin, fileManager, databaseHandler.getSchemaName());
				database.disconnect();
			}
		}
	}

	public List<DatabaseHandler> getDatabases() {
		return new ArrayList<>(databases);
	}

}
