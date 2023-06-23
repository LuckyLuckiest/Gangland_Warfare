package me.luckyraven.database;

import lombok.Getter;
import me.luckyraven.database.type.MySQL;
import me.luckyraven.database.type.SQLite;
import me.luckyraven.file.FileManager;
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public abstract class DatabaseHandler {

	public static final int MYSQL = 0, SQLITE = 1;
	private final   JavaPlugin  plugin;
	private         FileManager fileManager;
	private @Getter int         type;
	private @Getter Database    database;

	public DatabaseHandler(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public DatabaseHandler(JavaPlugin plugin, @NotNull FileManager fileManager) {
		this(plugin);
		this.fileManager = fileManager;
	}

	public abstract Map<String, Object> credentials();

	public abstract void createSchema();

	public abstract void createTables() throws SQLException;

	public abstract String getSchema();

	public void initialize() {
		if (database == null) return;

		createSchema();

		try {
			database.connect();

			createTables();

		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			rollbackConnection();
		} finally {
			if (database != null) database.disconnect();
		}
	}

	public void setType(int type) {
		this.type = type;

		Map<String, Object> credentials = credentials();

		switch (type) {
			case MYSQL -> {
				this.database = new MySQL(plugin);

				String schema = getSchema().lastIndexOf("\\") != -1 ? getSchema().substring(
						getSchema().lastIndexOf("\\") + 1) : getSchema();
				try {
					this.database.initialize(credentials, schema);
				} catch (SQLException exception) {
					this.database = null;
					backup();
					plugin.getLogger().info(
							String.format("Unable to connect to MySQL, switching to SQLite for '%s' database", schema));
				}
			}
			case SQLITE -> {
				this.database = new SQLite(plugin);
				createSchema();

				StringBuilder schemaLoc = new StringBuilder(plugin.getDataFolder().getAbsolutePath());
				schemaLoc.append("\\").append(getSchema()).append(".db");

				try {
					this.database.initialize(credentials, schemaLoc.toString());
				} catch (SQLException exception) {
					this.database = null;
					plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
					throw new RuntimeException(exception.getMessage());
				}
			}
			default -> throw new IllegalArgumentException("Unknown database type");
		}
	}

	public void rollbackConnection() {
		try {
			if (database.getConnection() != null) database.getConnection().rollback();
		} catch (SQLException exception) {
			plugin.getLogger().warning(
					UnhandledError.SQL_ERROR.getMessage() + ": Failed to rollback database connection, " +
							exception.getMessage());
		}
	}

	private void backup() {
		try {
			fileManager.checkFileLoaded("settings");

			FileConfiguration settings = fileManager.getFile("settings").getFileConfiguration();

			if (settings.getBoolean("Database.SQLite.Backup")) setType(SQLITE);

		} catch (IOException exception) {
			plugin.getLogger().warning(UnhandledError.FILE_LOADER_ERROR.getMessage() + ": " + exception.getMessage());
		}
	}

}
