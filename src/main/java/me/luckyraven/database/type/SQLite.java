package me.luckyraven.database.type;

import me.luckyraven.database.Database;
import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SQLite implements Database {

	private final JavaPlugin       plugin;
	private       Connection       connection;
	private       String           table;
	private final List<String>     tableNames;
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

		dataSource.setUrl("jdbc:sqlite:");
	}

	@Override
	public boolean switchSchema(String schema) throws SQLException {
		return false;
	}

	@Override
	public boolean schemaExists(String schema) {
		return false;
	}

	@Override
	public void createSchema(String name) {

	}

	@Override
	public void dropSchema(String name) throws SQLException {

	}

	@Override
	public Database table(String tableName) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");

		this.table = tableName;
		return this;
	}

	@Override
	public void connect() throws SQLException {

	}

	@Override
	public void disconnect() {

	}

	@Override
	public void createTable(String... values) throws SQLException {

	}

	@Override
	public void deleteTable() throws SQLException {

	}

	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public void setTableName(String newName) throws SQLException {

	}

	@Override
	public Database addColumn(String name, String columType) throws SQLException {
		return this;
	}

	@Override
	public Database insert(String[] columns, Object[] values, int[] types) throws SQLException {
		return this;
	}

	@Override
	public Object[] select(String row, Object[] parameters, int[] types, String[] columns) throws SQLException {
		return new Object[0];
	}

	@Override
	public Database update(String row, String... values) throws SQLException {
		return this;
	}

	@Override
	public int totalRows() throws SQLException {
		return 0;
	}

	@Override
	public Database delete(String column, String value) throws SQLException {
		return this;
	}

	@Override
	public ResultSet executeQuery(String statement) throws SQLException {
		return null;
	}

	@Override
	public void executeUpdate(String statement) throws SQLException {

	}

	@Override
	public void executeStatement(String statement) throws SQLException {

	}

	@Override
	public List<String> getTables() {
		return new ArrayList<>(tableNames);
	}

}
