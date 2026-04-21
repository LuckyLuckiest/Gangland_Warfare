package me.luckyraven.persistence.database;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.core.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

@CustomLog
public class DatabaseHelper {

	private final JavaPlugin      plugin;
	@Getter
	private final DatabaseHandler databaseHandler;
	private final Database        database;

	public DatabaseHelper(JavaPlugin plugin, @NotNull DatabaseHandler databaseHandler) {
		this.plugin          = plugin;
		this.databaseHandler = databaseHandler;
		this.database        = databaseHandler.getDatabase();
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
	 * @param queryRunnable using the functional interface {@link QueryRunnable} to execute operations for a
	 * 		database.
	 */
	public void runQueries(QueryRunnable queryRunnable) {
		if (database == null) return;

		synchronized (database) {
			boolean exceptionCaught = false;

			try {
				if (database.getConnection() == null) database.connect();
				queryRunnable.run(database);
			} catch (SQLException exception) {
				exceptionCaught = true;

				log.warn("{}: {}", UnhandledError.SQL_ERROR, exception.getMessage(), exception);
			} catch (Throwable throwable) {
				exceptionCaught = true;

				log.warn("{}: {}", UnhandledError.ERROR, throwable.getMessage(), throwable);
			} finally {
				if (exceptionCaught) rollbackConnection();
				if (database.getConnection() != null && !database.handlesConnectionPool()) database.disconnect();
			}
		}
	}

	public void runQueriesAsync(QueryRunnable queryRunnable) {
		runQueriesAsync(queryRunnable, null);
	}

	public void runQueriesAsync(QueryRunnable queryRunnable, Runnable onComplete) {
		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				runQueriesOnComplete(queryRunnable, onComplete);
			});
			return;
		}

		runQueriesOnComplete(queryRunnable, onComplete);
	}

	public void rollbackConnection() {
		try {
			if (database != null && database.getConnection() != null) {
				var conn = database.getConnection();
				if (!conn.getAutoCommit()) {
					conn.rollback();
					conn.setAutoCommit(true);
				}
			}
		} catch (SQLException exception) {
			log.warn("{}: Failed to rollback database connection, {}", UnhandledError.SQL_ERROR,
			         exception.getMessage());
		}
	}

	private void runQueriesOnComplete(QueryRunnable queryRunnable, Runnable onComplete) {
		runQueries(queryRunnable);
		if (onComplete != null) onComplete.run();
	}

	@FunctionalInterface
	public interface QueryRunnable {

		void run(Database database) throws SQLException;

	}

}
