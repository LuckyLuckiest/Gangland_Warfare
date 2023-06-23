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

	public DatabaseManager() {
		databases = new ArrayList<>();
	}

	public void addDatabase(DatabaseHandler database) {
		databases.add(database);
	}

	public void initializeDatabases() {
		for (DatabaseHandler database : databases) database.initialize();
	}

	public void startBackup(JavaPlugin plugin, FileManager fileManager, String db, boolean mysqlConn) {
		try {
			// tightly coupled to settings
			fileManager.checkFileLoaded("settings");

			FileConfiguration settings = fileManager.getFile("settings").getFileConfiguration();

			if (settings.getBoolean("Database.SQLite.Backup") && mysqlConn) {
				// TODO create a backup

				plugin.getLogger().info(String.format("Start a backup for '%s' database", db));
			}

		} catch (IOException exception) {
			plugin.getLogger().warning(UnhandledError.FILE_LOADER_ERROR.getMessage() + ": " + exception.getMessage());
		}
	}

	public List<DatabaseHandler> getDatabases() {
		return new ArrayList<>(databases);
	}

}
