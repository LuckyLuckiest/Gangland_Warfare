package me.luckyraven.persistence.database.type;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.luckyraven.persistence.database.Database;

import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MySQL implements Database {

	private final List<String> tableNames;

	private String host, database, username, password;
	private int        port;
	private Connection connection;
	private String     table;

	private HikariDataSource dataSource;

	public MySQL() {
		this.table      = null;
		this.tableNames = new ArrayList<>();
	}

	@Override
	public void initialize(Map<String, Object> credentials, String schema) throws SQLException {
		this.host     = (String) credentials.get("host");
		this.port     = (int) credentials.get("port");
		this.username = (String) credentials.get("username");
		this.password = (String) credentials.get("password");
		this.database = schema;

		HikariConfig config = new HikariConfig();

		String url = String.format("jdbc:mysql://%s:%d/", host, port);

		config.setJdbcUrl(url);
		config.setUsername(username);
		config.setPassword(password);

		config.setDriverClassName("com.mysql.jdbc.Driver");

		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

		config.setMaximumPoolSize(10);
		config.setMinimumIdle(5);
		config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
		config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());

		try {
			testConnection(url);

			dataSource = new HikariDataSource(config);
		} catch (SQLException exception) {
			throw new SQLException("Unable to create DataSource, " + exception.getMessage());
		}
	}

	@Override
	public boolean switchSchema(String schema) throws SQLException {
		if (!schemaExists(schema)) throw new SQLException("Schema specified doesn't exist");
		if (dataSource == null) throw new SQLException("DataSource is null");

		HikariConfig config = new HikariConfig();

		String url = String.format("jdbc:mysql://%s:%d/%s", host, port, schema);

		config.setJdbcUrl(url);
		config.setUsername(username);
		config.setPassword(password);

		config.setDriverClassName("com.mysql.jdbc.Driver");

		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

		config.setMaximumPoolSize(10);
		config.setMinimumIdle(5);
		config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
		config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());

		try {
			testConnection(url);

			disconnect();

			this.database = schema;
			dataSource    = new HikariDataSource(config);
		} catch (SQLException exception) {
			throw new SQLException("Unable to switch DataSource, " + exception.getMessage());
		}

		connect();
		tableNames.addAll(getTableNames(connection));

		return true;
	}

	@Override
	public boolean schemaExists(String schema) throws SQLException {
		if (connection == null) throw new SQLException("No connection established");

		ResultSet resultSet = connection.getMetaData().getCatalogs();

		while (resultSet.next()) {
			String existingSchema = resultSet.getString("TABLE_CAT");
			if (existingSchema.equalsIgnoreCase(schema)) return true;
		}

		return false;
	}

	@Override
	public void createSchema(String name) throws SQLException {
		if (!isValidIdentifier(name)) throw new SQLException("Invalid schema name: " + name);
		executeStatement("CREATE DATABASE IF NOT EXISTS `" + name + "`;");
	}

	@Override
	public void dropSchema(String name) throws SQLException {
		if (!isValidIdentifier(name)) throw new SQLException("Invalid schema name: " + name);
		executeStatement("DROP DATABASE IF EXISTS `" + name + "`;");
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

	@Override
	public void testConnection(String url) throws SQLException {
		Connection conn = DriverManager.getConnection(url, username, password);
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

		try (ResultSet resultSet = connection.getMetaData().getColumns(database, null, table, null)) {
			while (resultSet.next()) columns.add(resultSet.getString("COLUMN_NAME"));
		}

		return columns;
	}

	@Override
	public String getStringDataType(int columnType, int size) {
		return switch (columnType) {
			case Types.TINYINT -> "TINYINT";
			case Types.SMALLINT -> "SMALLINT";
			case Types.INTEGER -> "INT";
			case Types.BIGINT -> "BIGINT";
			case Types.FLOAT -> "FLOAT";
			case Types.DOUBLE -> "DOUBLE";
			case Types.BOOLEAN -> "BOOLEAN";
			case Types.TIMESTAMP -> "TIMESTAMP";
			case Types.VARCHAR -> "VARCHAR(" + size + ")";
			default -> "VARCHAR(255)";
		};
	}
}
