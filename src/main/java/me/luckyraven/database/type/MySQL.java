package me.luckyraven.database.type;

import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import me.luckyraven.database.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MySQL implements Database {

	private final JavaPlugin   plugin;
	private final List<String> tableNames;

	private @Getter String host, database, username, password;
	private @Getter int port;

	private Connection       connection;
	private String           table;
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

		String url = String.format("jdbc:mysql://%s:%d/%s", host, port, database);

		config.setJdbcUrl(url);
		config.setUsername(username);
		config.setPassword(password);

		try {
			dataSource = new HikariDataSource(config);
		} catch (Exception exception) {
			throw new SQLException("Unable to create DataSource, " + exception.getMessage());
		}

		try {
			connect();
			tableNames.addAll(getTableNames());
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
		} finally {
			disconnect();
		}
	}

	@Override
	public boolean switchSchema(String schema) throws SQLException {
		if (!schemaExists(schema)) throw new SQLException("Schema specified doesn't exist");

		try {
			HikariConfig config = new HikariConfig();

			String url = String.format("jdbc:mysql://%s:%d/%s", host, port, schema);

			config.setJdbcUrl(url);
			config.setUsername(username);
			config.setPassword(password);

			this.database = schema;

			dataSource.close();

			dataSource = new HikariDataSource(config);
		} catch (Exception exception) {
			plugin.getLogger().warning("Unable to switch DataSource, " + exception.getMessage());
			return false;
		}

		return true;
	}

	@Override
	public boolean schemaExists(String schema) {
		boolean exists = false;
		try {
			Object[] schemas = table("INFORMATION_SCHEMA.SCHEMATA").select("SCHEMA_NAME = ?", new Object[]{schema},
			                                                               new int[]{Types.VARCHAR},
			                                                               new String[]{"COUNT(*)"});
			if (schemas != null && schemas.length > 0) exists = (int) schemas[0] > 0;
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
		}
		return exists;
	}

	@Override
	public void createSchema(String name) {
		try {
			executeStatement("CREATE DATABASE " + name);
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
		}
	}

	@Override
	public void dropSchema(String name) {
		try {
			executeStatement("DROP DATABASE " + name);
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
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

		// Building query string
		StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS " + table + " (");
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
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
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

		try (PreparedStatement statement = connection.prepareStatement(query.toString())) {

			if (!row.isEmpty()) preparePlaceholderStatements(statement, placeholders, types);

			ResultSet    resultSet = statement.executeQuery();
			List<Object> results   = new ArrayList<>();

			Map<String, Class<?>> columnTypes = getColumnTypes(resultSet);

			while (resultSet.next()) {
				for (String column : columns) {
					Class<?> columnType = columnTypes.get(column);
					Object   value      = getValueFromResultSet(resultSet, column, columnType);
					results.add(value);
				}
			}

			return results.toArray();
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
			return new Object[0];
		}
	}

	@Override
	public Database update(String row, String... values) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(values, "Missing data");

		StringBuilder query = new StringBuilder("UPDATE " + table + " SET ");
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
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
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
		Preconditions.checkNotNull(table, "Invalid table");

		ResultSet resultSet = null;
		try (PreparedStatement query = connection.prepareStatement(statement)) {
			resultSet = query.executeQuery();
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
		}

		return resultSet;
	}

	@Override
	public void executeUpdate(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		try (PreparedStatement query = connection.prepareStatement(statement)) {
			query.executeUpdate();
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
		}
	}

	@Override
	public void executeStatement(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		try (PreparedStatement query = connection.prepareStatement(statement)) {
			query.execute();
		} catch (SQLException exception) {
			plugin.getLogger().warning("Unhandled error (sql): " + exception.getMessage());
		}
	}

	@Override
	public List<String> getTables() {
		return new ArrayList<>(tableNames);
	}

	private void preparePlaceholderStatements(PreparedStatement statement, Object[] placeholders, int[] types)
			throws SQLException {
		for (int i = 0; i < placeholders.length; i++) {
			Object value = placeholders[i];
			int    type  = types[i];
			int    index = i + 1;

			if (value == null) statement.setNull(index, type);
			else {
				switch (type) {
					case Types.INTEGER -> statement.setInt(index, (int) value);
					case Types.BIGINT -> statement.setLong(index, (long) value);
					case Types.FLOAT -> statement.setFloat(index, (float) value);
					case Types.DOUBLE -> statement.setDouble(index, (double) value);
					case Types.BOOLEAN -> statement.setBoolean(index, (boolean) value);
					case Types.DATE, Types.TIME, Types.TIMESTAMP -> {
						LocalDateTime dateTime  = (LocalDateTime) value;
						Timestamp     timestamp = Timestamp.valueOf(dateTime);
						statement.setTimestamp(index, timestamp);
					}
					case Types.VARCHAR, Types.LONGVARCHAR -> statement.setString(index, (String) value);
					default -> statement.setObject(index, value);
				}
			}
		}
	}

	private Map<String, Class<?>> getColumnTypes(ResultSet resultSet) throws SQLException {
		Map<String, Class<?>> columnTypes = new HashMap<>();
		ResultSetMetaData     metaData    = resultSet.getMetaData();
		int                   columnCount = metaData.getColumnCount();

		for (int i = 1; i <= columnCount; i++) {
			String   columnName = metaData.getColumnName(i);
			int      columnType = metaData.getColumnType(i);
			Class<?> javaType   = getJavaType(columnType);
			columnTypes.put(columnName, javaType);
		}

		return columnTypes;
	}

	private Class<?> getJavaType(int columnType) {
		return switch (columnType) {
			case Types.INTEGER -> Integer.class;
			case Types.BIGINT -> Long.class;
			case Types.FLOAT -> Float.class;
			case Types.DOUBLE -> Double.class;
			case Types.BOOLEAN -> Boolean.class;
			case Types.DATE, Types.TIME, Types.TIMESTAMP -> LocalDateTime.class;
			default -> String.class;
		};
	}

	private Object getValueFromResultSet(ResultSet resultSet, String columnName, Class<?> columnType)
			throws SQLException {
		Object value = resultSet.getObject(columnName);

		if (resultSet.wasNull()) value = null;
		else if (columnType.equals(LocalDateTime.class)) value = resultSet.getTimestamp(columnName).toLocalDateTime();
		else value = columnType.cast(value);

		return value;
	}


	private List<String> getTableNames() throws SQLException {
		List<String> tableNames = new ArrayList<>();

		DatabaseMetaData metaData = connection.getMetaData();
		try (ResultSet resultSet = metaData.getTables(null, null, null, new String[]{"TABLE"})) {
			while (resultSet.next()) {
				String tableName = resultSet.getString("TABLE_NAME");
				tableNames.add(tableName);
			}
		}
		return tableNames;
	}

}
