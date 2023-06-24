package me.luckyraven.database;

import me.luckyraven.util.UnhandledError;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseHelper {

	private final JavaPlugin      plugin;
	private final DatabaseHandler databaseHandler;
	private final Database        database;

	public DatabaseHelper(JavaPlugin plugin, @NotNull DatabaseHandler databaseHandler) {
		this.plugin = plugin;
		this.databaseHandler = databaseHandler;
		this.database = databaseHandler.getDatabase();
	}

	public void runQueries(QueryRunnable queryRunnable) {
		if (database == null) return;

		try {
			database.connect();
			queryRunnable.run(database.getConnection());
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			rollbackConnection();
		} finally {
			try {
				if (database.getConnection() != null && databaseHandler.getType() != DatabaseHandler.MYSQL)
					database.disconnect();
			} catch (SQLException exception) {
				plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			}
		}
	}

	public void rollbackConnection() {
		try {
			if (database != null && database.getConnection() != null) database.getConnection().rollback();
		} catch (SQLException exception) {
			plugin.getLogger().warning(
					UnhandledError.SQL_ERROR.getMessage() + ": Failed to rollback database connection, " +
							exception.getMessage());
		}
	}

	public interface QueryRunnable {

		void run(Connection connection) throws SQLException;

	}

}
