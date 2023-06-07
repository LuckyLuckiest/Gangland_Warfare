package me.luckyraven.database.type;

import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.luckyraven.database.Database;
import me.luckyraven.file.FileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MySQL implements Database {

	private final JavaPlugin       plugin;
	private final String           name;
	private       Connection       connection;
	private       String           table;
	private final List<String>     tableNames;
	private       HikariDataSource dataSource;

	public MySQL(JavaPlugin plugin, String name) {
		this.plugin = plugin;
		this.name = name;
		this.table = null;
		this.tableNames = new ArrayList<>();
	}

	@Override
	public void initialize(FileManager manager) {
		try {
			manager.checkFileLoaded("settings");
		} catch (IOException exception) {
			plugin.getLogger().warning(exception.getMessage());
			return;
		}

		FileConfiguration configuration = manager.getFile("settings").getFileConfiguration();

		HikariConfig config = new HikariConfig();

		String sDb = "Database.MySQL";

		String builder = "jdbc:mysql://" + configuration.getString(sDb + ".Host");
		builder += ":" + configuration.getString(sDb + ".Port");
		builder += "/" + configuration.getString(sDb + ".Database");

		config.setJdbcUrl(builder);
		config.setUsername(configuration.getString(sDb + ".Username"));
		config.setPassword(configuration.getString(sDb + ".Password"));

		dataSource = new HikariDataSource(config);
	}

	@Override
	public Database table(String tableName) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		this.table = tableName;
		return this;
	}

	@Override
	public void connect() throws SQLException {
		if (connection != null) throw new SQLException("There is a connection not closed");

		connection = dataSource.getConnection();
	}

	@Override
	public void disconnect() {
		Preconditions.checkNotNull(connection, "No connection established");

		if (dataSource != null) {
			dataSource.close();
			table = null;
		}
	}

	@Override
	public void createTable(String... values) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(values, "Missing data");

		tableNames.add(table);

		// Building query string
		StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS ? (");
		for (int i = 0; i < values.length; i++) {
			query.append(values[i]);
			if (i < values.length - 1) query.append(", ");
		}
		query.append(");");

		try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
			statement.setString(1, table);
			statement.executeUpdate();
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}
	}

	@Override
	public void deleteTable() throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		String query = "DROP TABLE IF EXISTS ?;";

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setString(1, table);
			statement.executeUpdate();
			tableNames.remove(table);
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}
	}

	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setTableName(String newName) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		if (!tableNames.contains(table)) throw new SQLException("Table not found");

		String query = "ALTER TABLE ? RENAME TO ?;";

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setString(1, table);
			statement.setString(2, newName);
			statement.executeUpdate();

			for (int i = 0; i < tableNames.size(); i++)
				if (tableNames.get(i).equalsIgnoreCase(table)) {
					tableNames.set(i, newName);
					break;
				}
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}
	}

	@Override
	public Database addColumn(String name, String value) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		String query = "ALTER TABLE ? ADD COLUMN ? ?;";

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setString(1, table);
			statement.setString(2, name);
			statement.setString(3, value);
			statement.executeUpdate();
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}
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

		StringBuilder columnNames  = new StringBuilder("(");
		StringBuilder placeholders = new StringBuilder("(");
		for (int i = 0; i < columns.length; i++) {
			columnNames.append(columns[i]);
			placeholders.append("?");

			if (i < columns.length - 1) {
				columnNames.append(", ");
				placeholders.append(", ");
			}
		}
		columnNames.append(")");
		placeholders.append(")");

		String query = "INSERT INTO ? " + columnNames + " VALUES " + placeholders + ";";

		try (PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setString(1, table);
			for (int i = 0, index = i + 2; i < values.length; i++, index++) {
				Object value = values[i];
				int    type  = types[i];

				if (value == null) statement.setNull(index, type);
				else {
					switch (type) {
						case Types.INTEGER -> statement.setInt(index, (int) value);
						case Types.BIGINT -> statement.setLong(index, (long) value);
						case Types.DOUBLE -> statement.setDouble(index, (double) value);
						case Types.BOOLEAN -> statement.setBoolean(index, (boolean) value);
						default -> statement.setObject(index, value);
					}
				}
			}
			statement.executeUpdate();
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}

		return this;
	}

	@Override
	public Object[] select(String row, String... columns) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(columns, "Missing columns");

		StringBuilder query = new StringBuilder("SELECT ");
		for (int i = 0; i < columns.length; i++) {
			query.append(columns[i]);
			if (i < columns.length - 1) query.append(", ");
		}

		query.append(" FROM ?");
		if (!row.isEmpty()) query.append(" WHERE ?");
		query.append(";");

		try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
			statement.setString(1, table);
			if (!row.isEmpty()) statement.setString(2, row);

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
			plugin.getLogger().warning(exception.getMessage());
			return new Object[0];
		}
	}

	@Override
	public Database update(String row, String... values) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");
		Preconditions.checkNotNull(values, "Missing data");

		StringBuilder query = new StringBuilder("UPDATE ? SET ");
		for (int i = 0; i < values.length; i++) {
			query.append(values[i]);
			if (i < values.length - 1) query.append(", ");
		}
		query.append(" WHERE ?;");

		try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
			statement.setString(1, table);
			statement.setString(2, row);
			statement.executeUpdate();
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}

		return this;
	}

	@Override
	public int totalRows() {
		try {
			Object[] result = select("", "COUNT(*)");
			if (result.length > 0 && result[0] instanceof Number) {
				return ((Number) result[0]).intValue();
			}
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}
		return 0;
	}


	@Override
	public Database delete(String value) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		String query = "DELETE FROM ? ?;";
		try (PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setString(1, table);
			statement.setString(2, value);
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}

		return this;
	}

	@Override
	public ResultSet executeQuery(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		ResultSet resultSet = null;
		try (Statement query = connection.createStatement()) {
			resultSet = query.executeQuery(statement);
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}

		return resultSet;
	}

	@Override
	public Database executeUpdate(String statement) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		Preconditions.checkNotNull(table, "Invalid table");

		try (Statement query = connection.createStatement()) {
			query.executeUpdate(statement);
		} catch (SQLException exception) {
			plugin.getLogger().warning(exception.getMessage());
		}

		return this;
	}

	@Override
	public List<String> getTables() {
		return new ArrayList<>(tableNames);
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
			case Types.DOUBLE -> Double.class;
			case Types.BOOLEAN -> Boolean.class;
			default -> String.class;
		};
	}

	private Object getValueFromResultSet(ResultSet resultSet, String columnName, Class<?> columnType)
			throws SQLException {
		Object value;

		if (columnType.equals(Integer.class)) {
			value = resultSet.getInt(columnName);
		} else if (columnType.equals(Long.class)) {
			value = resultSet.getLong(columnName);
		} else if (columnType.equals(Double.class)) {
			value = resultSet.getDouble(columnName);
		} else if (columnType.equals(Boolean.class)) {
			value = resultSet.getBoolean(columnName);
		} else {
			value = resultSet.getString(columnName);
		}

		if (resultSet.wasNull()) {
			value = null;
		}

		return value;
	}

}
