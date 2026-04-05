package me.luckyraven.persistence.database;

import me.luckyraven.util.utilities.DatabaseUtil;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The interface Database.
 */
public interface Database {

	/**
	 * Initialize the database according to the given credentials and the specific database.
	 *
	 * @param credentials to enter the database.
	 * @param schema the schema name to enter.
	 *
	 * @throws SQLException the sql exception
	 */
	void initialize(Map<String, Object> credentials, String schema) throws SQLException;

	/**
	 * Switch between schemas if the given schema is true.
	 *
	 * @param schema the schema name to access.
	 *
	 * @return the boolean
	 *
	 * @throws SQLException the sql exception
	 */
	boolean switchSchema(String schema) throws SQLException;

	/**
	 * Schema exists boolean.
	 *
	 * @param schema the schema
	 *
	 * @return the boolean
	 *
	 * @throws SQLException the sql exception
	 */
	boolean schemaExists(String schema) throws SQLException;

	/**
	 * Create schema.
	 *
	 * @param name the name of the new schema
	 *
	 * @throws SQLException the sql exception
	 * @throws IOException  if the program failed to create a file
	 */
	void createSchema(String name) throws SQLException, IOException;

	/**
	 * Drop schema.
	 *
	 * @param name the name of the schema to drop.
	 *
	 * @throws SQLException the sql exception
	 */
	void dropSchema(String name) throws SQLException;

	/**
	 * Specify the name of the table that will be used to work on.
	 *
	 * @param tableName name of the table.
	 *
	 * @return {@link Database} class.
	 *
	 * @throws SQLException when there is no connection.
	 */
	Database table(String tableName) throws SQLException;

	/**
	 * Establish a connection to the database.
	 *
	 * @throws SQLException when there is already a connection established.
	 */
	void connect() throws SQLException;

	/**
	 * Disconnects from the current connection.
	 */
	void disconnect();

	/**
	 * Tests the connection of the sql database.
	 *
	 * @param url The database url link
	 *
	 * @throws SQLException the sql exception
	 */
	void testConnection(String url) throws SQLException;

	/**
	 * Creates a table for the specified file.
	 *
	 * @param values gets an array of string values and executes a query.
	 *
	 * @throws SQLException the sql exception
	 */
	void createTable(String... values) throws SQLException;

	/**
	 * Deletes a table for the specified file.
	 *
	 * @throws SQLException the sql exception
	 */
	void deleteTable() throws SQLException;

	/**
	 * The connection that has been established between server and sql.
	 *
	 * @return {@link Connection} to sql.
	 */
	Connection getConnection();

	/**
	 * Returns the table name currently selected via {@link #table(String)}, or {@code null} if none has been selected
	 * yet.
	 *
	 * @return the active table name.
	 */
	String getTable();

	/**
	 * Changes the name of the table.
	 *
	 * @param newName new name of the table.
	 *
	 * @throws SQLException the sql exception
	 */
	void setTableName(String newName) throws SQLException;

	/**
	 * Gets all the tables of the specified database.
	 *
	 * @return a list of all tables names.
	 */
	List<String> getTables();

	/**
	 * Gets all the columns of the specified table.
	 *
	 * @return a list of all column names.
	 *
	 * @throws SQLException the sql exception
	 */
	List<String> getColumns() throws SQLException;

	/**
	 * Gets the column data type as a string.
	 *
	 * @param columnType the column class
	 *
	 * @return the column data type
	 */
	String getStringDataType(int columnType, int size);

	/**
	 * Checks if the class handles connection pool.
	 *
	 * @return boolean value.
	 */
	default boolean handlesConnectionPool() {
		return true;
	}

	/**
	 * Adds a column to the specified table.<br/><br/>
	 *
	 * <pre>{@code
	 * Database database = ...
	 * database.connect();
	 *
	 * database.table("data").addColumn("bounty", "DOUBLE");
	 *
	 * database.disconnect();
	 * }
	 * </pre>
	 *
	 * @param name name of the new column.
	 * @param columType values that are used for this new column.
	 *
	 * @return database instance
	 *
	 * @throws SQLException the sql exception
	 */
	default Database addColumn(String name, String columType) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String t = getTable();
		if (t == null) throw new SQLException("No table selected");

		executeUpdate("ALTER TABLE " + t + " ADD COLUMN " + name + " " + columType + ";");
		return this;
	}

	/**
	 * Inserts new data to the specified table. Need to know the information of the table to add, or it might throw
	 * exceptions.<br/><br/>
	 *
	 * <pre>{@code
	 * Database database = ...
	 * database.connect();
	 *
	 * database.table("data").insert(new String[]{"name", "balance"},
	 *                               new Object[]{"xx", "20.0"},
	 *                               new int[]{Types.VARCHAR, Types.DOUBLE});
	 *
	 * database.disconnect();
	 * }
	 * </pre>
	 *
	 * @param columns column names.
	 * @param values each value information.
	 * @param types each column data type, use {@link Types} to specify the data type.
	 *
	 * @return database instance
	 *
	 * @throws SQLException the sql exception
	 */
	default Database insert(String[] columns, Object[] values, int[] types) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String t = getTable();
		if (t == null) throw new SQLException("No table selected");
		if (columns == null) throw new NullPointerException("Missing columns");
		if (values == null) throw new NullPointerException("Missing data");
		if (types == null) throw new NullPointerException("Missing data types");
		if (columns.length != values.length || columns.length != types.length)
			throw new IllegalArgumentException("Invalid columns, values, and types data parameters");

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

		String query = "INSERT INTO " + t + " (" + columnNames + ") VALUES (" + placeholders + ");";
		try (PreparedStatement statement = conn.prepareStatement(query)) {
			preparePlaceholderStatements(statement, values, types, 0);
			statement.executeUpdate();
		}
		return this;
	}

	/**
	 * Selects data from the table specified and returns an array of objects from that table. It is very important that
	 * the values to be inserted should have a placeholder of '?', and if available they should be specified into the
	 * '{@code parameters}' parameter.<br/><br/>
	 *
	 * <pre>{@code
	 * Database database = ...
	 * database.connect();
	 *
	 * Object[] info = database.table("data").select("uuid = ?",
	 * 												 new Object[]{"xxxx-xxxx-xxxxxxxx"},
	 *                                               new int[]{Types.CHAR}
	 *                                               new String[]{"name", "balance"});
	 *
	 * database.disconnect();
	 * }
	 * </pre>
	 *
	 * @param row the specific row for a value that you need.
	 * @param placeholders placeholder values.
	 * @param types each placeholder type.
	 * @param columns which values you need information from.
	 *
	 * @return array of objects according to the length of columns provided.
	 *
	 * @throws SQLException the sql exception
	 */
	default Object[] select(String row, Object[] placeholders, int[] types, String[] columns) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String t = getTable();
		if (t == null) throw new SQLException("No table selected");
		if (row == null) throw new NullPointerException("Missing row");
		int count = (int) row.chars().filter(c -> c == '?').count();
		if (placeholders == null) throw new NullPointerException("Missing placeholders");
		if (types == null) throw new NullPointerException("Missing data types");
		if (count != placeholders.length || count != types.length)
			throw new IllegalArgumentException("Invalid placeholders, and types data parameters");
		if (columns == null) throw new NullPointerException("Missing columns");

		StringBuilder query = new StringBuilder("SELECT ");
		for (int i = 0; i < columns.length; i++) {
			query.append(columns[i]);
			if (i < columns.length - 1) query.append(", ");
		}
		query.append(" FROM ").append(t);
		if (!row.isEmpty()) query.append(" WHERE ").append(row);
		query.append(";");

		List<String> cols = new ArrayList<>(List.of(columns));
		if (columns.length == 1 && columns[0].equals("*")) cols = new ArrayList<>(getColumns());

		try (PreparedStatement statement = conn.prepareStatement(query.toString())) {
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

	/**
	 * Selects all the rows from the table specified and returns a list of an array of objects from that table.
	 *
	 * @return a list of all the rows array
	 *
	 * @throws SQLException the sql exception
	 */
	default List<Object[]> selectAll() throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String t = getTable();
		if (t == null) throw new SQLException("No table selected");

		String query = "SELECT * FROM " + t + ";";
		try (PreparedStatement statement = conn.prepareStatement(query)) {
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

	/**
	 * Selects all rows from the table and returns them with columns in the order specified by {@code orderedColumns}.
	 * <p>
	 * Unlike {@link #selectAll()}, which issues {@code SELECT *} and returns values in the physical DB column order,
	 * this overload issues {@code SELECT col1, col2, ...} with an explicit column list so that the returned
	 * {@code Object[]} indices always match the caller-supplied order regardless of how the table was physically laid
	 * out (e.g. after an {@code ALTER TABLE ADD COLUMN} appended a column to the end).
	 *
	 * @param orderedColumns the column names to select, in the desired output order
	 *
	 * @return a list of rows; each {@code Object[]} has values at the same positions as {@code orderedColumns}
	 *
	 * @throws SQLException the sql exception
	 */
	default List<Object[]> selectAll(String[] orderedColumns) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String t = getTable();
		if (t == null) throw new SQLException("No table selected");

		String cols  = String.join(", ", orderedColumns);
		String query = "SELECT " + cols + " FROM " + t + ";";

		try (PreparedStatement statement = conn.prepareStatement(query)) {
			ResultSet             resultSet   = statement.executeQuery();
			List<Object[]>        results     = new ArrayList<>();
			Map<String, Class<?>> columnTypes = getColumnTypes(resultSet);

			while (resultSet.next()) {
				Object[] row = new Object[orderedColumns.length];
				for (int i = 0; i < orderedColumns.length; i++) {
					String   col  = orderedColumns[i];
					Class<?> type = columnTypes.get(col);
					row[i] = getValueFromResultSet(resultSet, col, type);
				}
				results.add(row);
			}
			return results;
		}
	}

	/**
	 * Updates the value in the specified <i>row</i> in the database, additionally the <i>values</i> specified are used
	 * to specify the column name to be specifically updated.<br/><br/>
	 *
	 * <pre>{@code
	 * Database database = ...
	 * database.connect();
	 *
	 * database.table("data").update("id = ?",
	 *                               new Object[]{1},
	 *                               new int[]{Types.INTEGER},
	 *                               new String[]{"name", "balance"},
	 *                               new Object[]{"xx", 20D},
	 *                               new int[]{Types.VARCHAR, Types.DOUBLE});
	 *
	 * database.disconnect();
	 * }
	 * </pre>
	 *
	 * @param row the specific row that will be updated in the database
	 * @param rowPlaceholders the row placeholders
	 * @param rowTypes the row data types
	 * @param columns the columns
	 * @param colPlaceholders the columns placeholders
	 * @param colTypes the columns data types
	 *
	 * @return database instance
	 *
	 * @throws SQLException the sql exception
	 */
	default Database update(String row, Object[] rowPlaceholders, int[] rowTypes, String[] columns,
	                        Object[] colPlaceholders, int[] colTypes) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String t = getTable();
		if (t == null) throw new SQLException("No table selected");
		if (row == null) throw new NullPointerException("Missing row");
		int count = (int) row.chars().filter(c -> c == '?').count();
		if (rowPlaceholders == null) throw new NullPointerException("Missing row placeholders");
		if (rowTypes == null) throw new NullPointerException("Missing row types");
		if (count != rowPlaceholders.length || count != rowTypes.length)
			throw new IllegalArgumentException("Invalid row placeholders, and types data parameters");
		if (columns == null) throw new NullPointerException("Missing columns");
		if (colPlaceholders == null) throw new NullPointerException("Missing columns placeholders");
		if (colTypes == null) throw new NullPointerException("Missing columns types");
		if (columns.length != colPlaceholders.length || columns.length != colTypes.length)
			throw new IllegalArgumentException("Invalid columns placeholders, and types data parameters");

		StringBuilder query = new StringBuilder("UPDATE ").append(t).append(" SET ");
		for (int i = 0; i < columns.length; i++) {
			query.append(columns[i]).append(" = ?");
			if (i < columns.length - 1) query.append(", ");
		}
		query.append(" WHERE ").append(row).append(";");

		try (PreparedStatement statement = conn.prepareStatement(query.toString())) {
			preparePlaceholderStatements(statement, colPlaceholders, colTypes, 0);
			if (!row.isEmpty()) preparePlaceholderStatements(statement, rowPlaceholders, rowTypes, columns.length);
			statement.executeUpdate();
		}
		return this;
	}

	/**
	 * Gets the total rows of the specified table.
	 *
	 * @return length of the table provided.
	 */
	default int totalRows() throws SQLException {
		Object[] result = select("", new Object[]{ }, new int[]{ }, new String[]{"COUNT(*)"});
		if (result.length > 0 && result[0] instanceof Number n) return n.intValue();
		return 0;
	}

	/**
	 * To delete all the data from the table leave <i><b>value</i></b> empty, and if you specify the specified row
	 * using
	 * <i><b>WHERE column_name=value</b></i>.
	 *
	 * @param column the specific column.
	 * @param value all data from the table or specific data.
	 * @param type the type of the value provided.
	 *
	 * @return database instance
	 *
	 * @throws SQLException the sql exception
	 */
	default Database delete(String column, Object value, int type) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String t = getTable();
		if (t == null) throw new SQLException("No table selected");

		if (column.isEmpty()) {
			executeUpdate("DELETE FROM " + t + ";");
		} else {
			String query = "DELETE FROM " + t + " WHERE " + column + " = ?;";
			try (PreparedStatement statement = conn.prepareStatement(query)) {
				preparePlaceholderStatements(statement, new Object[]{value}, new int[]{type}, 0);
				statement.executeUpdate();
			}
		}
		return this;
	}

	/**
	 * Executes a query that you wish to execute.
	 *
	 * @param statement the statement provided needs SQL experience.
	 *
	 * @return returns the result found.
	 *
	 * @throws SQLException the sql exception
	 */
	default ResultSet executeQuery(String statement) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		PreparedStatement query = conn.prepareStatement(statement);
		return query.executeQuery();
	}

	/**
	 * Executes an update to table that you wish to execute.
	 *
	 * @param statement the statement provided needs SQL experience.
	 *
	 * @throws SQLException the sql exception
	 */
	default void executeUpdate(String statement) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		try (PreparedStatement query = conn.prepareStatement(statement)) {
			query.executeUpdate();
		}
	}

	/**
	 * Execute a statement to the table.
	 *
	 * @param statement the statement provided needs SQL experience.
	 *
	 * @throws SQLException the sql exception
	 */
	default void executeStatement(String statement) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		try (PreparedStatement query = conn.prepareStatement(statement)) {
			query.execute();
		}
	}

	/**
	 * Gets columns data type.
	 *
	 * @param columns the columns
	 *
	 * @return the column data type
	 *
	 * @throws SQLException the sql exception
	 */
	default List<Integer> getColumnsDataType(String[] columns) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String t = getTable();
		if (t == null) throw new SQLException("No table selected");

		StringBuilder query = new StringBuilder("SELECT ");
		for (int i = 0; i < columns.length; i++) {
			query.append(columns[i]);
			if (i < columns.length - 1) query.append(", ");
		}
		query.append(" FROM ").append(t).append(";");

		List<String> cols = new ArrayList<>(List.of(columns));
		if (columns.length == 1 && columns[0].equals("*")) cols = new ArrayList<>(getColumns());

		Map<String, Class<?>> columnTypes;
		try (ResultSet resultSet = executeQuery(query.toString())) {
			columnTypes = getColumnTypes(resultSet);
		}

		List<Integer> dataTypes = new ArrayList<>();
		for (String columnName : cols) {
			Class<?> columnType = columnTypes.get(columnName);
			dataTypes.add(DatabaseUtil.getColumnType(columnType));
		}
		return dataTypes;
	}

	/**
	 * Deletes rows matching all supplied conditions combined with AND.
	 *
	 * <p>The {@code whereClause} must use {@code ?} placeholders for every value, e.g.
	 * {@code "uuid = ? AND active = ?"}. Builders such as {@link me.luckyraven.persistence.database.query.QueryBuilder}
	 * construct this string automatically so callers rarely need to call this method directly.
	 *
	 * @param whereClause parameterized WHERE expression (without the "WHERE" keyword).
	 * @param placeholders values bound to the {@code ?} placeholders in order.
	 * @param types JDBC type constants from {@link java.sql.Types} for each placeholder.
	 *
	 * @return this database instance.
	 *
	 * @throws SQLException the sql exception
	 */
	default Database delete(String whereClause, Object[] placeholders, int[] types) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String currentTable = getTable();
		if (currentTable == null) throw new SQLException("No table selected");

		String query = "DELETE FROM " + currentTable + " WHERE " + whereClause + ";";
		try (PreparedStatement statement = conn.prepareStatement(query)) {
			preparePlaceholderStatements(statement, placeholders, types, 0);
			statement.executeUpdate();
		}
		return this;
	}

	/**
	 * Selects all rows that match the given WHERE conditions and returns each row as a separate {@code Object[]}. This
	 * complements {@link #selectAll()} with optional filtering.
	 *
	 * <p>Pass an empty {@code whereClause} (or call {@link #selectAll()} directly) to retrieve
	 * every row without filtering.
	 *
	 * @param whereClause parameterized WHERE expression (without the "WHERE" keyword).
	 * @param placeholders values bound to the {@code ?} placeholders in order.
	 * @param types JDBC type constants from {@link java.sql.Types} for each placeholder.
	 *
	 * @return a list of rows; each row is an {@code Object[]} ordered by the table's column definition.
	 *
	 * @throws SQLException the sql exception
	 */
	default List<Object[]> selectAll(String whereClause, Object[] placeholders, int[] types) throws SQLException {
		Connection conn = getConnection();
		if (conn == null) throw new SQLException("There is no connection");
		String currentTable = getTable();
		if (currentTable == null) throw new SQLException("No table selected");

		String query = "SELECT * FROM " + currentTable;
		if (whereClause != null && !whereClause.isEmpty()) query += " WHERE " + whereClause;
		query += ";";

		try (PreparedStatement statement = conn.prepareStatement(query)) {
			if (whereClause != null && !whereClause.isEmpty()) preparePlaceholderStatements(statement, placeholders,
			                                                                                types, 0);

			ResultSet             resultSet   = statement.executeQuery();
			Map<String, Class<?>> columnTypes = getColumnTypes(resultSet);
			int                   columnCount = resultSet.getMetaData().getColumnCount();
			List<Object[]>        results     = new ArrayList<>();

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

	/**
	 * Prepare placeholder statements by converting the placeholders into their appropriate data type.
	 *
	 * @param statement the statement
	 * @param placeholders the placeholders
	 * @param types the data types
	 * @param startPos the start pos
	 *
	 * @throws SQLException the sql exception
	 */
	default void preparePlaceholderStatements(PreparedStatement statement, Object[] placeholders, int[] types,
	                                          int startPos) throws SQLException {
		for (int i = 0; i < placeholders.length; i++) {
			Object value = placeholders[i];
			int    type  = types[i];
			int    index = startPos + i + 1;

			if (value == null) statement.setNull(index, type);
			else switch (type) {
				case Types.TINYINT -> statement.setByte(index, value instanceof String s ?
				                                               Byte.parseByte(s) :
				                                               ((Number) value).byteValue());
				case Types.SMALLINT -> statement.setShort(index, value instanceof String s ?
				                                                 Short.parseShort(s) :
				                                                 ((Number) value).shortValue());
				case Types.INTEGER -> statement.setInt(index, value instanceof String s ?
				                                              Integer.parseInt(s) :
				                                              ((Number) value).intValue());
				case Types.BIGINT -> statement.setLong(index, value instanceof String s ?
				                                              Long.parseLong(s) :
				                                              ((Number) value).longValue());
				case Types.FLOAT, Types.REAL -> statement.setFloat(index, value instanceof String s ?
				                                                          Float.parseFloat(s) :
				                                                          ((Number) value).floatValue());
				case Types.DOUBLE, Types.NUMERIC -> statement.setDouble(index, value instanceof String s ?
				                                                               Double.parseDouble(s) :
				                                                               ((Number) value).doubleValue());
				case Types.BOOLEAN -> {
					switch (value) {
						case Boolean b -> statement.setBoolean(index, (boolean) value);
						case Integer integer -> statement.setBoolean(index, integer != 0);
						case Number number -> statement.setBoolean(index, number.intValue() != 0);
						default -> statement.setBoolean(index, Boolean.parseBoolean(String.valueOf(value)));
					}
				}
				case Types.DATE, Types.TIME, Types.TIMESTAMP -> {
					LocalDateTime dateTime  = (LocalDateTime) value;
					Timestamp     timestamp = Timestamp.valueOf(dateTime);
					statement.setTimestamp(index, timestamp);
				}
				case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
				     Types.LONGNVARCHAR -> statement.setString(index, String.valueOf(value));
				default -> statement.setObject(index, value);
			}
		}
	}

	/**
	 * Gets the specified attribute data type from the current table.
	 *
	 * @param resultSet the result set
	 *
	 * @return the column types
	 *
	 * @throws SQLException the sql exception
	 */
	default Map<String, Class<?>> getColumnTypes(ResultSet resultSet) throws SQLException {
		Map<String, Class<?>> columnTypes = new HashMap<>();
		ResultSetMetaData     metaData    = resultSet.getMetaData();
		int                   columnCount = metaData.getColumnCount();

		for (int i = 1; i <= columnCount; i++) {
			String   columnName = metaData.getColumnName(i);
			int      columnType = metaData.getColumnType(i);
			Class<?> javaType   = DatabaseUtil.getJavaType(columnType);

			columnTypes.put(columnName, javaType);
		}

		return columnTypes;
	}

	/**
	 * Gets the value appropriate data type.
	 *
	 * @param resultSet the result set
	 * @param columnName the column name
	 * @param columnType the column type
	 *
	 * @return the value from result set
	 *
	 * @throws SQLException the sql exception
	 */
	default Object getValueFromResultSet(ResultSet resultSet, String columnName, Class<?> columnType) throws
			SQLException {
		Object value = resultSet.getObject(columnName);

		if (resultSet.wasNull()) value = null;
		else if (columnType.equals(LocalDateTime.class)) value = resultSet.getTimestamp(columnName).toLocalDateTime();
		else if (columnType.equals(Boolean.class)) {
			int intValue = resultSet.getInt(columnName);
			value = intValue != 0;
		} else if (columnType.equals(Long.class) && value instanceof Integer) value = ((Integer) value).longValue();
		else value = columnType.cast(value);

		return value;
	}


	/**
	 * Gets the database all table names.
	 *
	 * @param connection the connection
	 *
	 * @return the table names
	 *
	 * @throws SQLException the sql exception
	 */
	default List<String> getTableNames(Connection connection) throws SQLException {
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

	/**
	 * Creates a uniform way to create a list in the used sql.
	 *
	 * @param values information to use
	 *
	 * @return values joined using a comma as delimiter
	 */
	default String createList(List<String> values) {
		return String.join(",", values);
	}

	/**
	 * Breaks down the list used by the uniform delimiter
	 *
	 * @param list values with delimiters
	 *
	 * @return a list of data
	 */
	default List<String> getList(String list) {
		return Arrays.stream(list.split(",")).toList();
	}

	/**
	 * Validates that an identifier (table name, schema name, column name) contains only safe characters. This prevents
	 * SQL injection in DDL statements where parameterized queries cannot be used.
	 */
	default boolean isValidIdentifier(String identifier) {
		if (identifier == null || identifier.isEmpty()) return false;
		// Allow only alphanumeric characters and underscores
		return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
	}

}
