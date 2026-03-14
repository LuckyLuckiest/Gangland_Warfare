package me.luckyraven.persistence.support;

import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.DatabaseSettingsProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Minimal {@link DatabaseHandler} for integration tests.
 *
 * <p>{@link #getSchema()} returns a bare name (e.g. {@code "test_schema"}).
 * When the handler is set to SQLite mode, {@code DatabaseHandler.enforceType(SQLITE)} builds the full file path as
 * {@code dataFolder/test_schema.db} automatically.
 *
 * <p>{@link #createSchema()}, {@link #createTables()}, and {@link #insertInitialData()}
 * are all no-ops - tables are created explicitly inside each test.
 */
public final class TestDatabaseHandler extends DatabaseHandler {

	private final String schemaName;

	public TestDatabaseHandler(JavaPlugin plugin, DatabaseSettingsProvider settings, String schemaName) {
		super(plugin, settings);
		this.schemaName = schemaName;
	}

	/**
	 * SQLite .db files are created automatically by HikariCP; nothing to do here.
	 */
	@Override
	public void createSchema() throws SQLException, IOException { }

	/**
	 * Tables are set up explicitly inside each test.
	 */
	@Override
	public void createTables() throws SQLException { }

	@Override
	public void insertInitialData() throws SQLException { }

	@Override
	public String getSchema() { return schemaName; }
}
