package me.luckyraven.database.type;

import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.luckyraven.database.Database;
import me.luckyraven.file.FileHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SQLite implements Database {

	private final JavaPlugin   plugin;
	private final List<String> tableNames;

	private Connection connection;
	private String     table;

	private HikariDataSource dataSource;

	public SQLite(JavaPlugin plugin) {
		this.plugin = plugin;
		this.table = null;
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

//			testConnection(schema);

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

//			testConnection(schema);

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

	@Deprecated
	@Override
	public void testConnection(String url) throws SQLException {
		File file = new File(url);

		if (!file.exists()) throw new SQLException("Database not found!");

		Connection conn = DriverManager.getConnection(url);
		conn.close();
	}

	@Override
	public boolean handlesConnectionPool() {
		return true;
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
			preparePlaceholderStatements(statement, values, types, 0);
			statement.executeUpdate();
		}

		return this;
	}

	@Override
	public Object[] select(String row, Object[] placeholders, int[] types, String[] columns) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(row, "Missing row");
		int count = (int) row.chars().filter(c -> c == '?').count();
		Preconditions.checkNotNull(placeholders, "Missing placeholders");
		Preconditions.checkNotNull(types, "Missing data types");
		Preconditions.checkArgument(count == placeholders.length && count == types.length,
		                            "Invalid placeholders, and types data parameters");
		Preconditions.checkNotNull(columns, "Missing columns");

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
			if (!row.isEmpty()) preparePlaceholderStatements(statement, placeholders, types, 0);

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
		}
	}

	@Override
	public Database update(String row, Object[] rowPlaceholders, int[] rowTypes, String[] columns,
	                       Object[] colPlaceholders, int[] colTypes) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		Preconditions.checkNotNull(row, "Missing row");
		int count = (int) row.chars().filter(c -> c == '?').count();
		Preconditions.checkNotNull(rowPlaceholders, "Missing row placeholders");
		Preconditions.checkNotNull(rowTypes, "Missing row types");
		Preconditions.checkArgument(count == rowPlaceholders.length && count == rowTypes.length,
		                            "Invalid row placeholders, and types data parameters");

		Preconditions.checkNotNull(columns, "Missing columns");
		Preconditions.checkNotNull(colPlaceholders, "Missing columns placeholders");
		Preconditions.checkNotNull(colTypes, "Missing columns types");
		Preconditions.checkArgument(columns.length == colPlaceholders.length && columns.length == colTypes.length,
		                            "Invalid columns placeholders, and types data parameters");

		StringBuilder query = new StringBuilder("UPDATE ").append(table).append(" SET ");
		for (int i = 0; i < columns.length; i++) {
			query.append(columns[i]).append(" = ?");
			if (i < columns.length - 1) query.append(", ");
		}
		query.append(" WHERE ").append(row).append(";");

		try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
			preparePlaceholderStatements(statement, colPlaceholders, colTypes, 0);
			if (!row.isEmpty()) preparePlaceholderStatements(statement, rowPlaceholders, rowTypes, columns.length);

			statement.executeUpdate();
		}

		return this;
	}

	@Override
	public int totalRows() throws SQLException {
		Object[] result = select("", new Object[]{}, new int[]{}, new String[]{"COUNT(*)"});
		if (result.length > 0 && result[0] instanceof Number) {
			return ((Number) result[0]).intValue();
		}
		return 0;
	}

	@Override
	public Database delete(String column, String value) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		StringBuilder query = new StringBuilder("DELETE FROM " + table);
		if (!column.isEmpty()) query.append(" WHERE ").append(column).append(" = ").append(value);
		query.append(";");

		executeUpdate(query.toString());

		return this;
	}

	@Override
	public ResultSet executeQuery(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		PreparedStatement query = connection.prepareStatement(statement);
		return query.executeQuery();
	}

	@Override
	public void executeUpdate(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		try (PreparedStatement query = connection.prepareStatement(statement)) {
			query.executeUpdate();
		}
	}

	@Override
	public void executeStatement(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		try (PreparedStatement query = connection.prepareStatement(statement)) {
			query.execute();
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
		String       query   = "PRAGMA table_info(" + table + ");";

		try (PreparedStatement statement = connection.prepareStatement(query);
		     ResultSet resultSet = statement.executeQuery()) {
			while (resultSet.next()) columns.add(resultSet.getString("name"));
		}

		return columns;
	}

	@Override
	public List<Integer> getColumnsDataType(String[] columns) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		StringBuilder query = new StringBuilder("SELECT ");
		for (int i = 0; i < columns.length; i++) {
			query.append(columns[i]);
			if (i < columns.length - 1) query.append(", ");
		}

		query.append(" FROM ").append(table).append(";");

		List<String> cols = new ArrayList<>(List.of(columns));
		if (columns.length == 1 && columns[0].equals("*")) cols = new ArrayList<>(getColumns());

		Map<String, Class<?>> columnTypes;

		try (ResultSet resultSet = executeQuery(query.toString())) {
			columnTypes = getColumnTypes(resultSet);
		}

		List<Integer> dataTypes = new ArrayList<>();
		for (String columnName : cols) {
			Class<?> columnType = columnTypes.get(columnName);
			dataTypes.add(getColumnType(columnType));
		}

		return dataTypes;
	}

}
