package me.luckyraven.database;

import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	public void startBackup(JavaPlugin plugin, String db) throws SQLException {
		if (SettingAddon.isSqliteBackup()) {
			for (DatabaseHandler handler : databases) {
				switch (handler.getType()) {
					case DatabaseHandler.MYSQL -> backup(handler, () -> handler.enforceType(DatabaseHandler.SQLITE));
					case DatabaseHandler.SQLITE -> {
						try {
							backup(handler, () -> handler.enforceType(DatabaseHandler.MYSQL));
						} catch (RuntimeException exception) {
							plugin.getLogger().info(
									String.format("Failed to create a backup in MySQL in '%s' database.",
									              handler.getSchemaName()));
						}
					}
				}
			}

			plugin.getLogger().info(String.format("Backup done for '%s' database", db));
		}
	}

	public void closeConnections() throws SQLException {
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

	private Map<String, List<Object[]>> databaseInformation(DatabaseHandler handler) throws SQLException {
		Map<String, List<Object[]>> data = new HashMap<>();

		for (String table : handler.getDatabase().getTables()) {
			Database config = handler.getDatabase().table(table);

			data.put(table, config.selectAll());
		}

		return data;
	}

	private void backup(DatabaseHandler handler, @NotNull Runnable runnable) throws SQLException {
		// save all the databases data in a map, name: data
		Map<String, List<Object[]>> data = databaseInformation(handler);

		// check if database exist, if they don't handle them
		runnable.run();

		// add all the data from the map to the sqlite connection
		DatabaseHelper helper = new DatabaseHelper(plugin, handler);

		helper.runQueries(database -> {
			for (String table : data.keySet()) {
				Database config = database.table(table);

				String[]      columns         = config.getColumns().toArray(String[]::new);
				List<Integer> columnsDataType = config.getColumnsDataType();

				int[] dataTypes = new int[columnsDataType.size()];
				for (int i = 0; i < dataTypes.length; i++)
					dataTypes[i] = columnsDataType.get(i);

				for (Object[] objects : data.get(table))
					config.insert(columns, objects, dataTypes);
			}
		});
	}

}
