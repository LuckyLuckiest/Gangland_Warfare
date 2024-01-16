package me.luckyraven.database;

import lombok.Getter;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseHelper {

	private final         JavaPlugin      plugin;
	@Getter private final DatabaseHandler databaseHandler;
	private final         Database        database;

	public DatabaseHelper(JavaPlugin plugin, @NotNull DatabaseHandler databaseHandler) {
		this.plugin = plugin;
		this.databaseHandler = databaseHandler;
		this.database = databaseHandler.getDatabase();
	}

	/**
	 * Takes care of connecting to the database and disconnecting from it. Because this plugin uses HikariCP, it will
	 * not disconnect the HikariPool until the plugin is disabled. <br/>
	 * <pre>{@code
	 * DatabaseHelper helper = new DatabaseHelper(plugin, handler);
	 * helper.runQueries(database -> {
	 *     database.table("data")
	 *             .createTable("name TEXT", "age INT");
	 *
	 *     database.table("data")
	 *             .insert(new String[]{"name", "age"},
	 *                     new Object[]{"User", 20},
	 *                     new int[]{Types.VARCHAR, Types.INTEGER});
	 * });
	 * }</pre>
	 *
	 * @param queryRunnable using the functional interface {@link QueryRunnable} to execute operations for a database.
	 */
	public void runQueries(QueryRunnable queryRunnable) {
		if (database == null) return;

		boolean exceptionCaught = false;

		try {
			if (database.getConnection() == null) database.connect();
			queryRunnable.run(database);
		} catch (SQLException exception) {
			exceptionCaught = true;
			plugin.getLogger().log(Level.WARNING, UnhandledError.SQL_ERROR + ": " + exception.getMessage(), exception);
		} catch (Throwable throwable) {
			exceptionCaught = true;
			plugin.getLogger().log(Level.WARNING, UnhandledError.ERROR + ": " + throwable.getMessage(), throwable);
		} finally {
			if (exceptionCaught) rollbackConnection();
			if (database.getConnection() != null && !database.handlesConnectionPool()) database.disconnect();
		}
	}

	public void runQueriesAsync(QueryRunnable queryRunnable) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runQueries(queryRunnable));
	}

	public void rollbackConnection() {
		try {
			if (database != null && database.getConnection() != null) {
				database.getConnection().setAutoCommit(false);
				database.getConnection().rollback();
				database.getConnection().commit();
				database.getConnection().setAutoCommit(true);
			}
		} catch (SQLException exception) {
			plugin.getLogger()
				  .warning(UnhandledError.SQL_ERROR + ": Failed to rollback database connection, " +
						   exception.getMessage());
		}
	}

	@FunctionalInterface
	public interface QueryRunnable {

		void run(Database database) throws SQLException;

	}

}
