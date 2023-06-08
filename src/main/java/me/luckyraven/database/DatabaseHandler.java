package me.luckyraven.database;

import lombok.Getter;
import me.luckyraven.database.type.MySQL;
import me.luckyraven.database.type.SQLite;
import me.luckyraven.file.FileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public abstract class DatabaseHandler {

	public static final int MYSQL = 0, SQLITE = 1;
	private final   JavaPlugin  plugin;
	private         FileManager fileManager;
	private @Getter int         type;
	private @Getter Database    database;

	public DatabaseHandler(JavaPlugin plugin, int type) {
		this.plugin = plugin;
		setType(type);
	}

	public DatabaseHandler(JavaPlugin plugin, int type, FileManager fileManager) {
		this(plugin, type);
		this.fileManager = fileManager;
	}

	public abstract Map<String, Object> credentials();

	public abstract void tables() throws SQLException;

	public void initialize() {
		try {
			database.connect();
			database.getConnection().setAutoCommit(false);

			tables();

			database.getConnection().commit();
			plugin.getLogger().info("Connection to database has been established!");
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
			rollbackConnection();
		} finally {
			database.disconnect();
		}
	}

	public void setType(int type) {
		this.type = type;
		switch (type) {
			case MYSQL -> {
				this.database = new MySQL(plugin);
				try {
					this.database.initialize(credentials(), "user");
				} catch (SQLException exception) {
					backup();
				}
			}
			case SQLITE -> {
				this.database = new SQLite(plugin);
				try {
					this.database.initialize(credentials(), "user");
				} catch (SQLException exception) {
					plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
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
					"Unhandled error (sql): Failed to rollback database connection, " + exception.getMessage());
		}
	}

	private void backup() {
		try {
			fileManager.checkFileLoaded("settings");

			FileConfiguration settings = fileManager.getFile("settings").getFileConfiguration();

			if (settings.getBoolean("Database.SQLite.Backup")) setType(SQLITE);
		} catch (IOException ioException) {
			plugin.getLogger().warning("Unhandled error (file loader): " + ioException.getMessage());
		}
	}

}
