package me.luckyraven.database;

import lombok.Getter;
import me.luckyraven.database.type.MySQL;
import me.luckyraven.database.type.SQLite;
import me.luckyraven.file.FileManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;

public abstract class DatabaseHandler {

	public static final int MYSQL = 0, SQLITE = 1;
	private final   JavaPlugin plugin;
	private @Getter int        type;
	private @Getter Database   database;

	public DatabaseHandler(JavaPlugin plugin, int type) {
		this.plugin = plugin;
		setType(type);
	}

	public abstract Map<String, Object> credentials();

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

	public void setType(int type) {
		this.type = type;
		switch (type) {
			case MYSQL -> {
				this.database = new MySQL(plugin);
				this.database.initialize(credentials(), "user");
			}
			case SQLITE -> {
				this.database = new SQLite(plugin);
				this.database.initialize(credentials(), "user");
			}
			default -> throw new IllegalArgumentException("Unknown database type");
		}
	}

}
