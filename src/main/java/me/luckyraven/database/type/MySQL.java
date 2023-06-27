package me.luckyraven.database.type;

import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import me.luckyraven.database.Database;
import me.luckyraven.util.UnhandledError;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MySQL implements Database {

	private final JavaPlugin   plugin;
	private final List<String> tableNames;

	@Getter
	private String host, database, username, password;
	@Getter
	private int port;

	private Connection connection;
	private String     table;

	private HikariDataSource dataSource;

	public MySQL(JavaPlugin plugin) {
		this.plugin = plugin;
		this.table = null;
		this.tableNames = new ArrayList<>();
	}

	@Override
	public void initialize(Map<String, Object> credentials, String schema) throws SQLException {
		this.host = (String) credentials.get("host");
		this.port = (int) credentials.get("port");
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
			// check if there is a connection to the database
			Connection conn = DriverManager.getConnection(url, username, password);
			conn.close();

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
			// Check if there is a connection to the database
			Connection conn = DriverManager.getConnection(url, username, password);
			conn.close();

			disconnect();

			this.database = schema;
			dataSource = new HikariDataSource(config);
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unable to switch DataSource, " + exception.getMessage());
		}

		try {
			connect();
			tableNames.addAll(getTableNames(connection));
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		}

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
		try {
			executeStatement("CREATE DATABASE IF NOT EXISTS " + name);
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		}
	}

	@Override
	public void dropSchema(String name) throws SQLException {
		try {
			executeStatement("DROP DATABASE IF EXISTS " + name);
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		}
	}

	@Override
	public Database table(String tableName) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		this.table = tableName;
		return this;
	}

	@Override
	public void connect() throws SQLException {
		Preconditions.checkNotNull(dataSource, "DataSource can't be null");
		if (connection != null) throw new SQLException("There is a connection not closed");

		connection = dataSource.getConnection();
	}

	@Override
	public void disconnect() {
		Preconditions.checkNotNull(dataSource, "DataSource can't be null");
		Preconditions.checkNotNull(connection, "No connection established");

		dataSource.close();
		connection = null;
		table = null;
	}

	@Override
	public void createTable(String... values) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(values, "Missing data");

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
		Preconditions.checkNotNull(table, "Invalid table");

		String query = "DROP TABLE IF EXISTS " + table + ";";

		executeUpdate(query);
		tableNames.remove(table);
	}

	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public void setTableName(String newName) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		if (!tableNames.contains(table)) throw new SQLException("Table not found");

		String query = "ALTER TABLE " + table + " RENAME TO " + newName + ";";

		executeUpdate(query);
		for (int i = 0; i < tableNames.size(); i++)
			if (tableNames.get(i).equalsIgnoreCase(table)) {
				tableNames.set(i, newName);
				break;
			}

		table = newName;
	}

	@Override
	public Database addColumn(String name, String columType) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		String query = "ALTER TABLE " + table + " ADD COLUMN " + name + " " + columType + ";";

		executeUpdate(query);

		return this;
	}

	@Override
	public Database insert(String[] columns, Object[] values, int[] types) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(columns, "Missing columns");
		Preconditions.checkNotNull(values, "Missing data");
		Preconditions.checkNotNull(types, "Missing data types");
		Preconditions.checkArgument(columns.length == values.length && columns.length == types.length,
		                            "Invalid columns, values, and types data parameters");

		StringBuilder columnNames  = new StringBuilder();
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < columns.length; i++) {
			columnNames.append(columns[i]);
			placeholders.append("?");

			if (i < columns.length - 1) {
				columnNames.append(", ");
				placeholders.append(", ");
			}
		}

		String query = "INSERT INTO " + table + " (" + columnNames + ") VALUES (" + placeholders + ");";

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			preparePlaceholderStatements(statement, values, types);
			statement.executeUpdate();
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
		}

		return this;
	}

	@Override
	public Object[] select(String row, Object[] placeholders, int[] types, String[] columns) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(columns, "Missing columns");
		Preconditions.checkNotNull(placeholders, "Missing placeholders");
		Preconditions.checkNotNull(types, "Missing data types");
		Preconditions.checkArgument(placeholders.length == types.length,
		                            "Invalid placeholders, and types data parameters");

		StringBuilder query = new StringBuilder("SELECT ");
		for (int i = 0; i < columns.length; i++) {
			query.append(columns[i]);
			if (i < columns.length - 1) query.append(", ");
		}

		query.append(" FROM ").append(table);
		if (!row.isEmpty()) query.append(" WHERE ").append(row);
		query.append(";");

		List<String> cols = new ArrayList<>(List.of(columns));
		if (columns.length == 1 && columns[0].equals("*")) cols = new ArrayList<>(getColumns());

		try (PreparedStatement statement = connection.prepareStatement(query.toString())) {

			if (!row.isEmpty()) preparePlaceholderStatements(statement, placeholders, types);

			ResultSet    resultSet = statement.executeQuery();
			List<Object> results   = new ArrayList<>();

			Map<String, Class<?>> columnTypes = getColumnTypes(resultSet);

			while (resultSet.next()) {
				for (String column : cols) {
					Class<?> columnType = columnTypes.get(column);
					Object   value      = getValueFromResultSet(resultSet, column, columnType);
					results.add(value);
				}
			}

			return results.toArray();
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			return new Object[0];
		}
	}

	@Override
	public List<Object[]> selectAll() throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		String query = "SELECT * FROM " + table + ";";

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			ResultSet      resultSet = statement.executeQuery();
			List<Object[]> results   = new ArrayList<>();

			Map<String, Class<?>> columnTypes = getColumnTypes(resultSet);
			int                   columnCount = resultSet.getMetaData().getColumnCount();

			while (resultSet.next()) {
				Object[] row = new Object[columnCount];
				for (int i = 1; i <= columnCount; i++) {
					String   columnName = resultSet.getMetaData().getColumnName(i);
					Class<?> columnType = columnTypes.get(columnName);
					row[i - 1] = getValueFromResultSet(resultSet, columnName, columnType);
				}
				results.add(row);
			}

			return results;
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			return new ArrayList<>();
		}
	}

	@Override
	public Database update(String row, String... values) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(values, "Missing data");

		StringBuilder query = new StringBuilder("UPDATE ").append(table).append(" SET ");
		for (int i = 0; i < values.length; i++) {
			query.append(values[i]);
			if (i < values.length - 1) query.append(", ");
		}
		query.append(" WHERE ").append(row).append(";");

		executeUpdate(query.toString());

		return this;
	}

	@Override
	public int totalRows() {
		try {
			Object[] result = select("", new Object[]{}, new int[]{}, new String[]{"COUNT(*)"});
			if (result.length > 0 && result[0] instanceof Number) {
				return ((Number) result[0]).intValue();
			}
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
		}
		return 0;
	}


	@Override
	public Database delete(String column, String value) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		StringBuilder query = new StringBuilder("DELETE FROM " + table);
		if (!column.isEmpty()) query.append(" WHERE ").append(column).append(" = ");
		query.append(value).append(";");

		executeUpdate(query.toString());

		return this;
	}

	@Override
	public ResultSet executeQuery(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		ResultSet resultSet;
		try (PreparedStatement query = connection.prepareStatement(statement)) {
			resultSet = query.executeQuery();
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		}

		return resultSet;
	}

	@Override
	public void executeUpdate(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		try (PreparedStatement query = connection.prepareStatement(statement)) {
			query.executeUpdate();
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		}
	}

	@Override
	public void executeStatement(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		try (PreparedStatement query = connection.prepareStatement(statement)) {
			query.execute();
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		}
	}

	@Override
	public List<String> getTables() {
		return new ArrayList<>(tableNames);
	}

	@Override
	public List<String> getColumns() throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		List<String> columns = new ArrayList<>();

		try (ResultSet resultSet = connection.getMetaData().getColumns(database, null, table, null)) {
			while (resultSet.next()) columns.add(resultSet.getString("COLUMN_NAME"));
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		}

		return columns;
	}

}
