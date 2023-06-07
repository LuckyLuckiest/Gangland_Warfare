package me.luckyraven.database;

import org.bukkit.plugin.java.JavaPlugin;

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

	public DatabaseHandler getDatabase(String name) {
		for (DatabaseHandler database : databases) if (database.fileName().equalsIgnoreCase(name)) return database;
		return null;
	}

	public void initializeDatabases() {
		for (DatabaseHandler database : databases) database.initialize();
	}

	public List<DatabaseHandler> getDatabases() {
		return new ArrayList<>(databases);
	}

}
