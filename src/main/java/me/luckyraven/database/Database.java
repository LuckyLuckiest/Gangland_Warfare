package me.luckyraven.database;

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
	 * @param schema      the schema name to enter.
	 * @throws SQLException the sql exception
	 */
	void initialize(Map<String, Object> credentials, String schema) throws SQLException;

	/**
	 * Switch between schemas if the given schema is true.
	 *
	 * @param schema the schema name to access.
	 * @return the boolean
	 * @throws SQLException the sql exception
	 */
	boolean switchSchema(String schema) throws SQLException;

	/**
	 * Schema exists boolean.
	 *
	 * @param schema the schema
	 * @return the boolean
	 * @throws SQLException the sql exception
	 */
	boolean schemaExists(String schema) throws SQLException;

	/**
	 * Create schema.
	 *
	 * @param name the name of the new schema
	 * @throws SQLException the sql exception
	 * @throws IOException  if the program failed to create a file
	 */
	void createSchema(String name) throws SQLException, IOException;

	/**
	 * Drop schema.
	 *
	 * @param name the name of the schema to drop.
	 * @throws SQLException the sql exception
	 */
	void dropSchema(String name) throws SQLException;

	/**
	 * Specify the name of the table that will be used to work on.
	 *
	 * @param tableName name of the table.
	 * @return {@link Database} class.
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
	 * @throws SQLException the sql exception
	 */
	void testConnection(String url) throws SQLException;

	/**
	 * Creates a table for the specified file.
	 *
	 * @param values gets an array of string values and executes a query.
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
	 * Changes the name of the table.
	 *
	 * @param newName new name of the table.
	 * @throws SQLException the sql exception
	 */
	void setTableName(String newName) throws SQLException;

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
	 * }*</pre>
	 *
	 * @param name      name of the new column.
	 * @param columType values that are used for this new column.
	 * @return database instance
	 * @throws SQLException the sql exception
	 */
	Database addColumn(String name, String columType) throws SQLException;

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
	 * }*</pre>
	 *
	 * @param columns column names.
	 * @param values  each value information.
	 * @param types   each column data type, use {@link java.sql.Types} to specify the data type.
	 * @return database instance
	 * @throws SQLException the sql exception
	 */
	Database insert(String[] columns, Object[] values, int[] types) throws SQLException;

	/**
	 * Selects data from the table specified and returns an array of objects from that table.
	 * It is very important that the values to be inserted should have a placeholder of '?', and if
	 * available they should be specified into the '{@code parameters}' parameter.<br/><br/>
	 *
	 * <pre>{@code
	 * Database database = ...
	 * database.connect();
	 *
	 * Object[] info = database.table("data").select("uuid = ?",
	 * 												 new Object[]{1},
	 *                                               new int[]{Types.INTEGER}
	 *                                               new String[]{"name", "balance"});
	 *
	 * database.disconnect();
	 * }*</pre>
	 *
	 * @param row          the specific row for a value that you need.
	 * @param placeholders placeholder values.
	 * @param types        each placeholder type.
	 * @param columns      which values you need information from.
	 * @return array of objects according to the length of columns provided.
	 * @throws SQLException the sql exception
	 */
	Object[] select(String row, Object[] placeholders, int[] types, String[] columns) throws SQLException;

	/**
	 * Selects all the rows from the table specified and returns a list of an array of objects from that table.
	 *
	 * @return a list of all the rows array
	 * @throws SQLException the sql exception
	 */
	List<Object[]> selectAll() throws SQLException;

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
	 * }*</pre>
	 *
	 * @param row             the specific row that will be updated in the database
	 * @param rowPlaceholders the row placeholders
	 * @param rowTypes        the row data types
	 * @param columns         the columns
	 * @param colPlaceholders the columns placeholders
	 * @param colTypes        the columns data types
	 * @return database instance
	 * @throws SQLException the sql exception
	 */
	Database update(String row, Object[] rowPlaceholders, int[] rowTypes, String[] columns, Object[] colPlaceholders,
	                int[] colTypes) throws SQLException;

	/**
	 * Gets the total rows of the specified table.
	 *
	 * @return length of the table provided.
	 */
	int totalRows();

	/**
	 * To delete all the data from the table leave <i><b>value</i></b> empty, and if yor specify the specified row using
	 * <i><b>WHERE column_name=value</b></i>.
	 *
	 * @param column the specific column.
	 * @param value  all data from the table or specific data.
	 * @return database instance
	 * @throws SQLException the sql exception
	 */
	Database delete(String column, String value) throws SQLException;

	/**
	 * Executes a query that you wish to execute.
	 *
	 * @param statement the statement provided needs SQL experience.
	 * @return returns the result found.
	 * @throws SQLException the sql exception
	 */
	ResultSet executeQuery(String statement) throws SQLException;

	/**
	 * Executes an update to table that you wish to execute.
	 *
	 * @param statement the statement provided needs SQL experience.
	 * @throws SQLException the sql exception
	 */
	void executeUpdate(String statement) throws SQLException;

	/**
	 * Execute a statement to the table.
	 *
	 * @param statement the statement provided needs SQL experience.
	 * @throws SQLException the sql exception
	 */
	void executeStatement(String statement) throws SQLException;

	/**
	 * Gets all the tables of the specified database.
	 *
	 * @return a list of all tables names.
	 */
	List<String> getTables();

	/**
	 * Gets all the columns of the specified table.
	 *
	 * @return a list of all columns names.
	 * @throws SQLException the sql exception
	 */
	List<String> getColumns() throws SQLException;

	/**
	 * Prepare placeholder statements by converting the placeholders into their appropriate data type.
	 *
	 * @param statement    the statement
	 * @param placeholders the placeholders
	 * @param types        the data types
	 * @param startPos     the start pos
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
				case Types.TINYINT -> statement.setByte(index, (byte) value);
				case Types.SMALLINT -> statement.setShort(index, (short) value);
				case Types.INTEGER -> statement.setInt(index, (int) value);
				case Types.BIGINT -> statement.setLong(index, (long) value);
				case Types.FLOAT, Types.REAL -> statement.setFloat(index, (float) value);
				case Types.DOUBLE, Types.NUMERIC -> statement.setDouble(index, (double) value);
				case Types.BOOLEAN -> statement.setBoolean(index, (boolean) value);
				case Types.DATE, Types.TIME, Types.TIMESTAMP -> {
					LocalDateTime dateTime  = (LocalDateTime) value;
					Timestamp     timestamp = Timestamp.valueOf(dateTime);
					statement.setTimestamp(index, timestamp);
				}
				case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
						statement.setString(index, String.valueOf(value));
				default -> statement.setObject(index, value);
			}
		}
	}

	/**
	 * Gets the specified attribute data type from the current table.
	 *
	 * @param resultSet the result set
	 * @return the column types
	 * @throws SQLException the sql exception
	 */
	default Map<String, Class<?>> getColumnTypes(ResultSet resultSet) throws SQLException {
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

	/**
	 * Gets the java equivalent data type of the database used data types.
	 *
	 * @param columnType the column type
	 * @return the java type
	 */
	default Class<?> getJavaType(int columnType) {
		return switch (columnType) {
			case Types.TINYINT -> Byte.class;
			case Types.SMALLINT -> Short.class;
			case Types.INTEGER -> Integer.class;
			case Types.BIGINT -> Long.class;
			case Types.FLOAT, Types.REAL -> Float.class;
			case Types.DOUBLE, Types.NUMERIC -> Double.class;
			case Types.BOOLEAN, Types.BIT -> Boolean.class;
			case Types.DATE, Types.TIME, Types.TIMESTAMP -> LocalDateTime.class;
			default -> String.class;
		};
	}

	/**
	 * Gets the value appropriate data type.
	 *
	 * @param resultSet  the result set
	 * @param columnName the column name
	 * @param columnType the column type
	 * @return the value from result set
	 * @throws SQLException the sql exception
	 */
	default Object getValueFromResultSet(ResultSet resultSet, String columnName, Class<?> columnType)
			throws SQLException {
		Object value = resultSet.getObject(columnName);

		if (resultSet.wasNull()) value = null;
		else if (columnType.equals(LocalDateTime.class)) value = resultSet.getTimestamp(columnName).toLocalDateTime();
		else if (columnType.equals(Boolean.class)) {
			int intValue = resultSet.getInt(columnName);
			value = intValue != 0;
		} else value = columnType.cast(value);

		return value;
	}


	/**
	 * Gets the database all table names.
	 *
	 * @param connection the connection
	 * @return the table names
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
	 * @return values joined using a comma as delimiter
	 */
	default String createList(List<String> values) {
		return String.join(",", values);
	}

	/**
	 * Breaks down the list used by the uniform delimiter
	 *
	 * @param list values with delimiters
	 * @return a list of data
	 */
	default List<String> getList(String list) {
		return new ArrayList(Arrays.stream(list.split(",")).toList());
	}

}
