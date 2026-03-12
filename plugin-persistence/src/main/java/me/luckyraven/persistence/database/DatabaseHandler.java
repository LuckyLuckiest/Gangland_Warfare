package me.luckyraven.persistence.database;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.database.type.MySQL;
import me.luckyraven.persistence.database.type.SQLite;
import me.luckyraven.util.UnhandledError;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@Getter
@RequiredArgsConstructor
public abstract class DatabaseHandler {

	public static final int MYSQL = 0, SQLITE = 1;

	@Getter(AccessLevel.NONE)
	private final JavaPlugin               plugin;
	private final DatabaseSettingsProvider settings;

	private int      type;
	private Database database;

	public abstract void createSchema() throws SQLException, IOException;

	public abstract void createTables() throws SQLException;

	public abstract void insertInitialData() throws SQLException;

	public abstract String getSchema();

	public void initialize() {
		DatabaseHelper helper = new DatabaseHelper(plugin, this);

		helper.runQueries(db -> {
			try {
				createSchema();
				createTables();
			} catch (IOException exception) {
				log.warn("{}: {}", UnhandledError.FILE_CREATE_ERROR, exception.getMessage());
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

					throw new PluginException(exception.getMessage(), exception);
				}
			}
			case SQLITE -> {
				this.database = new SQLite(plugin);

				StringBuilder schemaLoc = new StringBuilder(plugin.getDataFolder().getAbsolutePath());
				schemaLoc.append(File.separator).append(getSchema()).append(".db");

				try {
					createSchema();

					this.database.initialize(credentials, schemaLoc.toString());
				} catch (SQLException | IOException exception) {
					this.database = null;
					UnhandledError inst = exception instanceof SQLException ?
										  UnhandledError.SQL_ERROR :
										  UnhandledError.FILE_CREATE_ERROR;

					log.warn("{}: {}", inst, exception.getMessage());

					throw new PluginException(exception.getMessage(), exception);
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
		return getSchema().lastIndexOf(File.separator) != -1 ?
			   getSchema().substring(getSchema().lastIndexOf(File.separator) + 1) :
			   getSchema();
	}

	private void useSQLite(String schema) {
		if (!settings.isSqliteFailedMysql()) return;

		setType(SQLITE);
		log.info("Referring to SQLite for '{}' database", schema);
	}

	private Map<String, Object> credentials() {
		Map<String, Object> map = new HashMap<>();

		switch (getType()) {
			case DatabaseHandler.MYSQL -> {
				map.put("host", settings.getMysqlHost());
				map.put("password", settings.getMysqlPassword());
				map.put("port", settings.getMysqlPort());
				map.put("username", settings.getMysqlUsername());
			}
			case DatabaseHandler.SQLITE -> {
			}
			default -> throw new IllegalArgumentException("Unknown database type");
		}

		return map;
	}


}
