package me.luckyraven.database;

import lombok.Getter;
import me.luckyraven.database.type.MySQL;
import me.luckyraven.database.type.SQLite;
import me.luckyraven.file.FileManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public abstract class DatabaseHandler {

	public static final int MYSQL = 0, SQLITE = 1;
	private final   JavaPlugin plugin;
	private @Getter int        type;
	private @Getter Database   database;

	public DatabaseHandler(JavaPlugin plugin, FileManager fileManager, int type) throws SQLException {
		this.plugin = plugin;
		setType(fileManager, type);
	}

	public abstract String fileName();

	public abstract void tables() throws SQLException;

	// Initialize database tables
	public void initialize() {
		try {
			database.connect();
			database.getConnection().setAutoCommit(false);
			tables();
			database.getConnection().commit();
			database.disconnect();
			plugin.getLogger().info("Connection to a database has been established!");
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
		}
	}

	public void setType(FileManager fileManager, int type) throws SQLException {
		this.type = type;
		switch (type) {
			case MYSQL -> {
				this.database = new MySQL(plugin, fileName());
				this.database.initialize(fileManager);
			}
			case SQLITE -> {
				this.database = new SQLite(plugin, fileName());
				this.database.initialize(fileManager);
			}
			default -> throw new SQLException("Unknown database type");
		}
	}

}
