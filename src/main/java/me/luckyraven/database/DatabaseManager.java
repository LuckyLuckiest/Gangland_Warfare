package me.luckyraven.database;

import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.plugin.java.JavaPlugin;

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

	public void startBackup(JavaPlugin plugin, String db) {
		if (SettingAddon.isSqliteBackup()) {
			for (DatabaseHandler handler : databases) {
				switch (handler.getType()) {
					case DatabaseHandler.MYSQL -> {
						// TODO create a backup from mysql to sqlite
						// save all the databases data in a map, name: data
						// check if SQLite files exist, if they don't create them
						// add all the data from the map to the sqlite connection
					}
					case DatabaseHandler.SQLITE -> {
						// TODO create a backup from sqlite to mysql
						// save all the databases data in a map, name: data
						// check if there can be a connection
					}
				}
			}

			plugin.getLogger().info(String.format("Backup done for '%s' database", db));
		}
	}

	public void closeConnections() {
		for (DatabaseHandler databaseHandler : databases) {
			Database database = databaseHandler.getDatabase();
			if (database != null && database.getConnection() != null) {
				startBackup(plugin, databaseHandler.getSchemaName());
				database.disconnect();
			}
		}
	}

	public List<DatabaseHandler> getDatabases() {
		return new ArrayList<>(databases);
	}

}
