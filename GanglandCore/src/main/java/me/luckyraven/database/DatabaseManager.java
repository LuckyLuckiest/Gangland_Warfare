package me.luckyraven.database;

import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

	private final List<DatabaseHandler> databases;
	private final JavaPlugin            plugin;

	public DatabaseManager(JavaPlugin plugin) {
		this.plugin    = plugin;
		this.databases = new ArrayList<>();
	}

	public void addDatabase(DatabaseHandler database) {
		databases.add(database);
	}

	public void initializeDatabases() {
		for (DatabaseHandler database : databases) database.initialize();
	}

	public void closeConnections() {
		for (DatabaseHandler databaseHandler : databases) {
			Database        database = databaseHandler.getDatabase();
			DatabaseHandler backup   = null;

			if (database != null && database.getConnection() != null) backup = startBackup(plugin, databaseHandler);

			if (backup != null && backup.getDatabase() != null &&
				backup.getDatabase().getConnection() != null) backup.getDatabase().disconnect();
		}
	}

	@Nullable
	public DatabaseHandler startBackup(JavaPlugin plugin, DatabaseHandler handler) {
		if (SettingAddon.isSqliteBackup()) try {
			switch (handler.getType()) {
				case DatabaseHandler.MYSQL -> backup(handler, DatabaseHandler.SQLITE);
				case DatabaseHandler.SQLITE -> backup(handler, DatabaseHandler.MYSQL);
			}

			plugin.getLogger().info(String.format("Backup done for '%s' database", handler.getSchemaName()));
		} catch (Throwable throwable) {
			String type = "";
			switch (handler.getType()) {
				// inverting the types since they will be inverse, according to the first switch statement
				case DatabaseHandler.MYSQL -> type = "SQLite";
				case DatabaseHandler.SQLITE -> type = "MySQL";
			}

			plugin.getLogger()
				  .info(String.format("Failed to create a backup for '%s' in '%s' database.", type,
									  handler.getSchema()));
		}

		return handler;
	}

	public List<DatabaseHandler> getDatabases() {
		return new ArrayList<>(databases);
	}

	private Map<String, List<Object[]>> databaseInformation(DatabaseHandler handler) throws SQLException {
		Map<String, List<Object[]>> data = new HashMap<>();

		for (String table : handler.getDatabase().getTables()) {
			Database config = handler.getDatabase().table(table);

			List<Object[]> info = new ArrayList<>();
			try {
				info.addAll(config.selectAll());
			} catch (SQLException ignored) {
			}

			if (!info.isEmpty()) data.put(table, config.selectAll());
		}

		// need to disable the database after gathering the information
		handler.getDatabase().disconnect();

		return data;
	}

	private void backup(DatabaseHandler handler, int databaseType) throws SQLException {
		// save all the database data in a map, name: data
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
				List<Integer> columnsDataType = config.getColumnsDataType(columns);

				int[] dataTypes = new int[columnsDataType.size()];
				for (int i = 0; i < dataTypes.length; i++)
					 dataTypes[i] = columnsDataType.get(i);

				// if they don't exist, we create them otherwise update the data
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
