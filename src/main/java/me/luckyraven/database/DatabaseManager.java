package me.luckyraven.database;

import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.plugin.java.JavaPlugin;

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

	public DatabaseHandler startBackup(JavaPlugin plugin, DatabaseHandler handler) {
		if (SettingAddon.isSqliteBackup()) try {
			switch (handler.getType()) {
				case DatabaseHandler.MYSQL -> backup(handler, DatabaseHandler.SQLITE);
				case DatabaseHandler.SQLITE -> backup(handler, DatabaseHandler.MYSQL);
			}

			plugin.getLogger().info(String.format("Backup done for '%s' database", handler.getSchemaName()));
		} catch (Exception exception) {
			plugin.getLogger().info(
					String.format("Failed to create a backup for MySQL in '%s' database.", handler.getSchema()));
		}

		return handler;
	}

	public void closeConnections() {
		for (DatabaseHandler databaseHandler : databases) {
			Database database = databaseHandler.getDatabase();
			if (database != null && database.getConnection() != null) {
				startBackup(plugin, databaseHandler).getDatabase().disconnect();
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

		// need to disable the database after gathering the information
		handler.getDatabase().disconnect();

		return data;
	}

	private void backup(DatabaseHandler handler, int databaseType) throws SQLException {
		// save all the databases data in a map, name: data
		Map<String, List<Object[]>> data = databaseInformation(handler);

		// check if a database exists if they don't handle them
		handler.enforceType(databaseType);

		// need to initialize the database to create the missing schemas
		handler.initialize();

		// add all the data from the map to the sqlite connection
		DatabaseHelper helper = new DatabaseHelper(plugin, handler);

		helper.runQueries(database -> {
			for (String table : data.keySet()) {
				Database config = database.table(table);

				String[]      columns         = config.getColumns().toArray(String[]::new);
				List<Integer> columnsDataType = config.getColumnsDataType(new String[]{"*"});

				int[] dataTypes = new int[columnsDataType.size()];
				for (int i = 0; i < dataTypes.length; i++)
					dataTypes[i] = columnsDataType.get(i);

				// if they don't exist, we create them otherwise just update the data
				for (Object[] objects : data.get(table)) {
					Object[] row = config.select(columns[0] + " = ?", new Object[]{objects[0]}, new int[]{dataTypes[0]},
					                             new String[]{"*"});

					if (row.length == 0) config.insert(columns, objects, dataTypes);
					else config.update(columns[0] + " = ?", new Object[]{objects[0]}, new int[]{dataTypes[0]}, columns,
					                   objects, dataTypes);
				}
			}
		});
	}

}
