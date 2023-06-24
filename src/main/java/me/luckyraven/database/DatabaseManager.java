package me.luckyraven.database;

import me.luckyraven.file.FileManager;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

	private final List<DatabaseHandler> databases;
	private final JavaPlugin            plugin;

	public DatabaseManager(JavaPlugin plugin) {
		databases = new ArrayList<>();
		this.plugin = plugin;
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

	public void closeConnections() {
		for (DatabaseHandler database : databases)
			try {
				if (database.getDatabase() != null && database.getDatabase().getConnection() != null)
					database.getDatabase().disconnect();
			} catch (SQLException exception) {
				plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			}
	}

	public List<DatabaseHandler> getDatabases() {
		return new ArrayList<>(databases);
	}

}
