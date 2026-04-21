package me.luckyraven.persistence.database;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

@CustomLog
public class DatabaseManager {

	private final List<DatabaseHandler>    databases;
	private final JavaPlugin               plugin;
	private final DatabaseSettingsProvider settings;

	public DatabaseManager(JavaPlugin plugin, DatabaseSettingsProvider settings) {
		this.plugin    = plugin;
		this.settings  = settings;
		this.databases = new ArrayList<>();
	}

	public void addDatabase(DatabaseHandler database) {
		databases.add(database);
	}

	public void initializeDatabases() {
		for (DatabaseHandler database : databases) database.initialize();

		log.debug("Database initialization complete.");
	}

	public void closeConnections() {
		for (DatabaseHandler databaseHandler : databases) {
			Database        database = databaseHandler.getDatabase();
			DatabaseHandler backup   = null;

			if (database != null && database.getConnection() != null) backup = startBackup(databaseHandler);

			if (backup != null && backup.getDatabase() != null &&
			    backup.getDatabase().getConnection() != null) backup.getDatabase().disconnect();
		}
	}

	@Nullable
	public DatabaseHandler startBackup(DatabaseHandler handler) {
		if (settings.isSqliteBackup()) {
			try {
				switch (handler.getType()) {
					case DatabaseHandler.MYSQL -> backup(handler, DatabaseHandler.SQLITE);
					case DatabaseHandler.SQLITE -> backup(handler, DatabaseHandler.MYSQL);
				}

				log.info("Backup done for '{}' database.", handler.getSchemaName());
			} catch (Throwable throwable) {
				String type = "";
				switch (handler.getType()) {
					// inverting the types since they will be inverse, according to the first switch statement
					case DatabaseHandler.MYSQL -> type = "SQLite";
					case DatabaseHandler.SQLITE -> type = "MySQL";
				}

				log.warn("Failed to create a backup for '{}' in '{}' database.", type, handler.getSchema());
			}
		}

		return handler;
	}

	public List<DatabaseHandler> getDatabases() {
		return Collections.unmodifiableList(databases);
	}

	private LinkedHashMap<String, List<Object[]>> databaseInformation(DatabaseHandler handler) throws SQLException {
		LinkedHashMap<String, List<Object[]>> data = new LinkedHashMap<>();

		for (String table : handler.getDatabase().getTables()) {
			Database config  = handler.getDatabase().table(table);
			String[] columns = config.getColumns().toArray(String[]::new);

			List<Object[]> info = new ArrayList<>();
			try {
				info.addAll(config.selectAll(columns));
			} catch (SQLException ignored) { }

			if (!info.isEmpty()) data.put(table, info);
		}

		// need to disable the database after gathering the information
		handler.getDatabase().disconnect();

		return data;
	}

	private void backup(DatabaseHandler handler, int databaseType) throws SQLException {
		// save all the database data in a map, name: data
		var data = databaseInformation(handler);

		// check if a database exists if they don't handle them
		handler.enforceType(databaseType);

		// need to initialize the database to create the missing schemas
		handler.initialize();

		// add all the data from the map to the sqlite connection
		DatabaseHelper helper = new DatabaseHelper(plugin, handler);

		helper.runQueries(database -> {
			// Temporarily disable foreign key checks for MySQL, enable for SQLite
			if (databaseType == DatabaseHandler.MYSQL) {
				database.executeStatement("SET FOREIGN_KEY_CHECKS=0");
			} else if (databaseType == DatabaseHandler.SQLITE) {
				database.executeStatement("PRAGMA foreign_keys = OFF");
			}

			try {
				backupAllDatabases(database, data);
			} finally {
				// Re-enable foreign key checks
				if (databaseType == DatabaseHandler.MYSQL) {
					database.executeStatement("SET FOREIGN_KEY_CHECKS=1");
				} else if (databaseType == DatabaseHandler.SQLITE) {
					database.executeStatement("PRAGMA foreign_keys = ON");
				}
			}
		});
	}

	private void backupAllDatabases(Database database, LinkedHashMap<String, List<Object[]>> data) throws SQLException {
		for (String table : data.keySet()) {
			Database config = database.table(table);

			String[]      columns         = config.getColumns().toArray(String[]::new);
			List<Integer> columnsDataType = config.getColumnsDataType(columns);

			int[] dataTypes = new int[columnsDataType.size()];

			for (int i = 0; i < dataTypes.length; i++) {
				dataTypes[i] = columnsDataType.get(i);
			}

			createDatabaseBackup(data, table, config, columns, dataTypes);
		}
	}

	private void createDatabaseBackup(LinkedHashMap<String, List<Object[]>> data, String table, Database config,
	                                  String[] columns, int[] dataTypes) {
		// if they don't exist, we create them otherwise update the data
		for (Object[] objects : data.get(table)) {
			try {
				Object[] row = config.select(columns[0] + " = ?", new Object[]{objects[0]}, new int[]{dataTypes[0]},
				                             new String[]{"*"});

				if (row.length == 0) {
					config.insert(columns, objects, dataTypes);
				} else {
					config.update(columns[0] + " = ?", new Object[]{objects[0]}, new int[]{dataTypes[0]}, columns,
					              objects, dataTypes);
				}
			} catch (SQLException exception) {
				log.warn("Failed to backup row in table '{}': {}", table, exception.getMessage());
			}
		}
	}

}
