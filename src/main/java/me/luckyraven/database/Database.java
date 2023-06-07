package me.luckyraven.database;

import me.luckyraven.file.FileManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface Database {

	void initialize(FileManager fileManager);

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
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void createTable(String... values);

	/**
	 * Deletes a table for the specified file.
	 *
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void deleteTable();

	/**
	 * The connection that has been established between server and sqlite.
	 *
	 * @return {@link Connection} to sqlite.
	 */
	Connection getConnection();

	/**
	 * Name of the database.
	 *
	 * @return gets the name of the database initialized.
	 */
	String getName();

	/**
	 * Changes the name of the table.
	 *
	 * @param newName new name of the table.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void setTableName(String newName);

	/**
	 * Adds a column to the specified table
	 *
	 * @param name  name of the new column.
	 * @param value values that are used for this new column.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void addColumn(String name, String value);

	/**
	 * Inserts new data to the specified table. Need to know the information of the table to add, or it might throw
	 * exceptions.
	 *
	 * @param values data types associated with names.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void insert(String... values);

	/**
	 * Selects data from the table specified and returns an array of objects from that table.
	 *
	 * @param row     the specific row for a value that you need.
	 * @param columns which values you need information from.
	 * @return array of objects according to the length of columns provided.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	Object[] select(String row, String... columns);

	/**
	 * Updates the value in the specified <i>row</i> in the database, additionally the <i>values</i> specified are used
	 * to specify the column name to be specifically updated.
	 *
	 * @param row    the specific row that will be updated in the database.
	 * @param values the specific columns that will be updated in the database.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void update(String row, String... values);

	/**
	 * Gets the total rows of the specified table.
	 *
	 * @return length of the table provided.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	int totalRows();

	/**
	 * To delete all the data from the table leave <i><b>value</i></b> empty, and if yor specify the specified row using
	 * <i><b>WHERE column_name=value</b></i>.
	 *
	 * @param value all data from the table or specific data.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void delete(String value);

	/**
	 * Executes a query that you wish to execute.
	 *
	 * @param statement the statement provided needs SQLite experience.
	 * @return returns the result found.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	ResultSet executeQuery(String statement);

	/**
	 * Executes an update to table that you wish to execute.
	 *
	 * @param statement the statement provided needs SQLite experience.
	 * @implNote Make sure that you connect to the database and then specify the table name, then when you finish
	 * everything you commit and disconnect the database.
	 */
	void executeUpdate(String statement);

	/**
	 * Gets all the tables of the specified database.
	 *
	 * @return a list of all tables names.
	 */
	List<String> getTables();

}

