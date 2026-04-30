package org.luckyraven.gangland.persistence.database.type;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.database.Database;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.Duration;
import java.util.*;

public class SQLite implements Database {

	private final JavaPlugin   plugin;
	private final List<String> tableNames;

	private Connection connection;
	private String     table;

	private HikariDataSource dataSource;

	public SQLite(JavaPlugin plugin) {
		this.plugin     = plugin;
		this.table      = null;
		this.tableNames = new ArrayList<>();
	}

	@Override
	public void initialize(Map<String, Object> credentials, String schema) throws SQLException {
		HikariConfig config = new HikariConfig();

		String url = "jdbc:sqlite:" + schema;

		config.setJdbcUrl(url);
		config.setDriverClassName("org.sqlite.JDBC");

		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

		config.setMaximumPoolSize(10);
		config.setMinimumIdle(5);
		config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
		config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());

		this.dataSource = new HikariDataSource(config);
	}

	@Override
	public boolean switchSchema(String schema) throws SQLException {
		if (!schemaExists(schema)) throw new SQLException("Schema specified doesn't exist");
		if (dataSource == null) throw new SQLException("DataSource is null");

		HikariConfig config = new HikariConfig();

		String url = "jdbc:sqlite:" + schema;

		config.setJdbcUrl(url);
		config.setDriverClassName("org.sqlite.JDBC");

		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

		config.setMaximumPoolSize(10);
		config.setMinimumIdle(5);
		config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
		config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());

		disconnect();

		this.dataSource = new HikariDataSource(config);

		return true;
	}

	@Override
	public boolean schemaExists(String schema) throws SQLException {
		if (connection == null) throw new SQLException("No connection established");

		ResultSet resultSet = connection.getMetaData().getCatalogs();

		while (resultSet.next()) {
			String existingSchema = resultSet.getString(1);
			if (existingSchema.equalsIgnoreCase(schema)) return true;
		}

		return false;
	}

	@Override
	public void createSchema(String name) throws IOException {
		FileHandler file = new FileHandler(plugin, name, "db");
		file.create(false);
	}

	@Override
	public void dropSchema(String name) throws SQLException {
		File file = new File(plugin.getDataFolder(), name + ".db");
		if (file.exists()) if (!file.delete()) throw new SQLException("Failed to drop schema: " + name);
	}

	@Override
	public Database table(String tableName) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		if (!isValidIdentifier(tableName)) throw new SQLException("Invalid table name: " + tableName);

		this.table = tableName;
		return this;
	}

	@Override
	public void connect() throws SQLException {
		if (dataSource == null) throw new NullPointerException("DataSource can't be null");
		if (connection != null) throw new SQLException("There is a connection not closed");

		connection = dataSource.getConnection();
	}

	@Override
	public void disconnect() {
		if (dataSource == null) throw new NullPointerException("DataSource can't be null");
		if (connection == null) throw new NullPointerException("No connection established");

		dataSource.close();
		connection = null;
		table      = null;
	}

	@Deprecated
	@Override
	public void testConnection(String url) throws SQLException {
		File file = new File(url);
		if (!file.exists()) throw new SQLException("Database not found!");

		Connection conn = DriverManager.getConnection(url);
		conn.close();
	}

	@Override
	public void createTable(String... values) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		if (table == null) throw new NullPointerException("Invalid table");
		if (values == null) throw new NullPointerException("Missing data");

		tableNames.add(table);

		StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(table).append(" (");
		for (int i = 0; i < values.length; i++) {
			query.append(values[i]);
			if (i < values.length - 1) query.append(", ");
		}
		query.append(");");

		executeUpdate(query.toString());
	}

	@Override
	public void deleteTable() throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		if (table == null) throw new NullPointerException("Invalid table");

		executeUpdate("DROP TABLE IF EXISTS " + table + ";");
		tableNames.remove(table);
	}

	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public String getTable() {
		return table;
	}

	@Override
	public void setTableName(String newName) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		if (table == null) throw new NullPointerException("Invalid table");
		if (!tableNames.contains(table)) throw new SQLException("Table not found");

		executeUpdate("ALTER TABLE " + table + " RENAME TO " + newName + ";");

		for (int i = 0; i < tableNames.size(); i++)
			if (tableNames.get(i).equalsIgnoreCase(table)) {
				tableNames.set(i, newName);
				break;
			}

		table = newName;
	}

	@Override
	public List<String> getTables() {
		return Collections.unmodifiableList(tableNames);
	}

	@Override
	public List<String> getColumns() throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		if (table == null) throw new NullPointerException("Invalid table");

		List<String> columns = new ArrayList<>();
		String       query   = "PRAGMA table_info(" + table + ");";

		try (PreparedStatement statement = connection.prepareStatement(query);
		     ResultSet resultSet = statement.executeQuery()) {
			while (resultSet.next()) columns.add(resultSet.getString("name"));
		}

		return columns;
	}

	@Override
	public String buildUpsertQuery(String[] conflictColumns, String[] allColumns) throws SQLException {
		if (table == null) throw new SQLException("No table selected");
		if (conflictColumns == null || conflictColumns.length == 0) {
			throw new SQLException("Cannot build UPSERT: no conflict columns provided");
		}

		Set<String> conflictSet = new HashSet<>(Arrays.asList(conflictColumns));

		StringBuilder columnNames  = new StringBuilder();
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < allColumns.length; i++) {
			columnNames.append(allColumns[i]);
			placeholders.append("?");
			if (i < allColumns.length - 1) {
				columnNames.append(", ");
				placeholders.append(", ");
			}
		}

		StringBuilder sql = new StringBuilder();
		sql.append("INSERT INTO ").append(table)
		   .append(" (").append(columnNames).append(") VALUES (").append(placeholders).append(")")
		   .append(" ON CONFLICT(").append(String.join(", ", conflictColumns)).append(")");

		List<String> updateColumns = new ArrayList<>();
		for (String col : allColumns) {
			if (!conflictSet.contains(col)) updateColumns.add(col);
		}

		if (updateColumns.isEmpty()) {
			sql.append(" DO NOTHING");
		} else {
			sql.append(" DO UPDATE SET ");
			for (int i = 0; i < updateColumns.size(); i++) {
				sql.append(updateColumns.get(i)).append(" = excluded.").append(updateColumns.get(i));
				if (i < updateColumns.size() - 1) sql.append(", ");
			}
		}

		return sql.append(";").toString();
	}

	@Override
	public String getStringDataType(int columnType, int size) {
		return switch (columnType) {
			case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BOOLEAN -> "INTEGER";
			case Types.BIGINT -> "BIGINT";
			case Types.FLOAT, Types.DOUBLE -> "REAL";
			default -> "TEXT";
		};
	}
}
