package me.luckyraven.database.type;

import com.google.common.base.Preconditions;
import me.luckyraven.database.Database;
import me.luckyraven.file.FileHandler;
import me.luckyraven.util.UnhandledError;
import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SQLite implements Database {

	private final JavaPlugin       plugin;
	private final List<String>     tableNames;
	private       Connection       connection;
	private       String           table;
	private       SQLiteDataSource dataSource;

	public SQLite(JavaPlugin plugin) {
		this.plugin = plugin;
		this.table = null;
		this.tableNames = new ArrayList<>();
	}

	@Override
	public void initialize(Map<String, Object> credentials, String schema) throws SQLException {
		SQLiteConfig config = new SQLiteConfig();
		dataSource = new SQLiteDataSource(config);

		dataSource.setUrl("jdbc:sqlite:" + schema);

		try {
			connect();
			tableNames.addAll(getTableNames(connection));

			if (dataSource.getConnection() == null) throw new SQLException("Failed to establish a valid connection");
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		} finally {
			disconnect();
		}
	}

	@Override
	public boolean switchSchema(String schema) throws SQLException {
		if (!schemaExists(schema)) throw new SQLException("Schema specified doesn't exist");
		if (connection == null) throw new SQLException("No connection established");

		connection.setSchema(schema);

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

		try {
			file.create(false);
		} catch (IOException exception) {
			plugin.getLogger().warning(UnhandledError.FILE_CREATE_ERROR.getMessage() + ": " + exception.getMessage());
			throw exception;
		}
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

		try {
			connection.close();
		} catch (SQLException exception) {
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
		}

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
	public Connection getConnection() throws SQLException {
		if (connection == null) throw new SQLException("No connection established");

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
			plugin.getLogger().warning(UnhandledError.SQL_ERROR.getMessage() + ": " + exception.getMessage());
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

}
