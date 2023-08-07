package me.luckyraven.database;

import lombok.Getter;
import me.luckyraven.database.type.MySQL;
import me.luckyraven.database.type.SQLite;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.UnhandledError;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public abstract class DatabaseHandler {

	public static final int MYSQL = 0, SQLITE = 1;

	private final JavaPlugin plugin;

	private @Getter int      type;
	private @Getter Database database;

	public DatabaseHandler(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public abstract void createSchema() throws SQLException, IOException;

	public abstract void createTables() throws SQLException;

	public abstract void insertInitialData() throws SQLException;

	public abstract String getSchema();

	// TODO work on a way that checks if the table values are similar or not,
	//  if they are not just create the new columns with a default value attached if it was null

	public void initialize() {
		DatabaseHelper helper = new DatabaseHelper(plugin, this);

		helper.runQueries(db -> {
			try {
				createSchema();
				createTables();
			} catch (IOException exception) {
				plugin.getLogger().warning(UnhandledError.FILE_CREATE_ERROR + ": " + exception.getMessage());
			}
		});

		helper.runQueries(db -> insertInitialData());
	}

	public void enforceType(int type) {
		this.type = type;

		Map<String, Object> credentials = credentials();

		switch (type) {
			case MYSQL -> {
				this.database = new MySQL();

				String schema = getSchema();

				try {
					this.database.initialize(credentials, schema);
				} catch (SQLException exception) {
					this.database = null;

					throw new RuntimeException(exception.getMessage());
				}
			}
			case SQLITE -> {
				this.database = new SQLite(plugin);

				StringBuilder schemaLoc = new StringBuilder(plugin.getDataFolder().getAbsolutePath());
				schemaLoc.append("\\").append(getSchema()).append(".db");

				try {
					createSchema();

					this.database.initialize(credentials, schemaLoc.toString());
				} catch (SQLException exception) {
					this.database = null;

					plugin.getLogger().warning(UnhandledError.SQL_ERROR + ": " + exception.getMessage());

					throw new RuntimeException(exception.getMessage());
				} catch (IOException exception) {
					this.database = null;

					plugin.getLogger().warning(UnhandledError.FILE_CREATE_ERROR + ": " + exception.getMessage());

					throw new RuntimeException(exception.getMessage());
				}
			}
			default -> throw new IllegalArgumentException("Unknown database type");
		}
	}

	public void setType(int type) {
		this.type = type;

		switch (type) {
			case MYSQL -> {
				try {
					enforceType(MYSQL);
				} catch (RuntimeException exception) {
					useSQLite(getSchema());
				}
			}
			case SQLITE -> enforceType(SQLITE);
			default -> throw new IllegalArgumentException("Unknown database type");
		}
	}

	public String getSchemaName() {
		return getSchema().lastIndexOf("\\") != -1 ? getSchema().substring(getSchema().lastIndexOf("\\") + 1)
		                                           : getSchema();
	}

	private void useSQLite(String schema) {
		if (!SettingAddon.isSqliteFailedMysql()) return;

		setType(SQLITE);
		plugin.getLogger().info(String.format("Referring to SQLite for '%s' database", schema));
	}

	private Map<String, Object> credentials() {
		Map<String, Object> map = new HashMap<>();

		switch (getType()) {
			case DatabaseHandler.MYSQL -> {
				map.put("host", SettingAddon.getMysqlHost());
				map.put("password", SettingAddon.getMysqlPassword());
				map.put("port", SettingAddon.getMysqlPort());
				map.put("username", SettingAddon.getMysqlUsername());
			}
			case DatabaseHandler.SQLITE -> {
			}
			default -> throw new IllegalArgumentException("Unknown database type");
		}

		return map;
	}


}
