package me.luckyraven.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	boolean switchSchema(String schema) throws SQLException;

	/**
	 * Schema exists boolean.
	 *
	 * @param schema the schema
	 * @return the boolean
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	boolean schemaExists(String schema);

	/**
	 * Create schema.
	 *
	 * @param name the name of the new schema
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void createSchema(String name);

	/**
	 * Drop schema.
	 *
	 * @param name the name of the schema to drop.
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
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
	 * Creates a table for the specified file.
	 *
	 * @param values gets an array of string values and executes a query.
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void createTable(String... values) throws SQLException;

	/**
	 * Deletes a table for the specified file.
	 *
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void deleteTable() throws SQLException;

	/**
	 * The connection that has been established between server and sqlite.
	 *
	 * @return {@link Connection} to sqlite.
	 */
	Connection getConnection();

	/**
	 * Changes the name of the table.
	 *
	 * @param newName new name of the table.
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void setTableName(String newName) throws SQLException;

	/**
	 * Adds a column to the specified table.<br/><br/>
	 *
	 * <pre>
	 *     Database database = new UserDatabase(...);
	 *     database.connect();
	 *
	 *     database.table("data").addColumn("bounty", "DOUBLE");
	 *
	 *     database.disconnect();
	 * </pre>
	 *
	 * @param name      name of the new column.
	 * @param columType values that are used for this new column.
	 * @return database instance
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	Database addColumn(String name, String columType) throws SQLException;

	/**
	 * Inserts new data to the specified table. Need to know the information of the table to add, or it might throw
	 * exceptions.<br/><br/>
	 *
	 * <pre>
	 *     Database database = new UserDatabase(...);
	 *     database.connect();
	 *
	 *     database.table("data").insert(new String[]{"name", "balance"},
	 *                                   new Object[]{"xx", "20.0"},
	 *                                   new int[]{Types.VARCHAR, Types.DOUBLE});
	 *
	 *     database.disconnect();
	 * </pre>
	 *
	 * @param columns column names.
	 * @param values  each value information.
	 * @param types   each column data type, use {@link java.sql.Types} to specify the data type.
	 * @return database instance
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	Database insert(String[] columns, Object[] values, int[] types) throws SQLException;

	/**
	 * Selects data from the table specified and returns an array of objects from that table.
	 * It is very important that the values to be inserted should have a placeholder of '?', and if
	 * available they should be specified into the '{@code parameters}' parameter.<br/><br/>
	 *
	 * <pre>
	 *     Database database = new UserDatabase(...);
	 *     database.connect();
	 *
	 *     Object[] info = database.table("data").select("uuid = ?",
	 *                                                   new Object[]{1},
	 *                                                   new int[]{Types.INTEGER}
	 *                                                   new String[]{"name", "balance"});
	 *
	 *     database.disconnect();
	 * </pre>
	 *
	 * @param row          the specific row for a value that you need.
	 * @param placeholders placeholder values.
	 * @param types        each placeholder type.
	 * @param columns      which values you need information from.
	 * @return array of objects according to the length of columns provided.
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	Object[] select(String row, Object[] placeholders, int[] types, String[] columns) throws SQLException;

	/**
	 * Updates the value in the specified <i>row</i> in the database, additionally the <i>values</i> specified are used
	 * to specify the column name to be specifically updated.<br/><br/>
	 *
	 * <pre>
	 *     Database database = new UserDatabase(...);
	 *     database.connect();
	 *
	 *     database.table("data").update("uuid = xx", "name = user1", "balance = 20.0");
	 *
	 *     database.disconnect();
	 * </pre>
	 *
	 * @param row    the specific row that will be updated in the database.
	 * @param values the specific columns that will be updated in the database.
	 * @return database instance
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	Database update(String row, String... values) throws SQLException;

	/**
	 * Gets the total rows of the specified table.
	 *
	 * @return length of the table provided.
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	int totalRows() throws SQLException;

	/**
	 * To delete all the data from the table leave <i><b>value</i></b> empty, and if yor specify the specified row using
	 * <i><b>WHERE column_name=value</b></i>.
	 *
	 * @param column the specific column.
	 * @param value  all data from the table or specific data.
	 * @return database instance
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	Database delete(String column, String value) throws SQLException;

	/**
	 * Executes a query that you wish to execute.
	 *
	 * @param statement the statement provided needs SQL experience.
	 * @return returns the result found.
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	ResultSet executeQuery(String statement) throws SQLException;

	/**
	 * Executes an update to table that you wish to execute.
	 *
	 * @param statement the statement provided needs SQL experience.
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void executeUpdate(String statement) throws SQLException;

	/**
	 * Execute a statement to the table.
	 *
	 * @param statement the statement provided needs SQL experience.
	 * @throws SQLException the sql exception
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void executeStatement(String statement) throws SQLException;

	/**
	 * Gets all the tables of the specified database.
	 *
	 * @return a list of all tables names.
	 */
	List<String> getTables();

}
