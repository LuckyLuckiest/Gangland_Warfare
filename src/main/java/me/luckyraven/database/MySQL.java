package me.luckyraven.database;

import me.luckyraven.Gangland;
import me.luckyraven.file.FileHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MySQL implements Database {

	private       Connection   connection;
	private final String       name;
	private final FileHandler  fileHandler;
	private final List<String> tableNames;
	private       String       table;

	public MySQL(FileHandler fileHandler) {
		this.fileHandler = fileHandler;
		this.name = fileHandler.getName();
		this.table = null;
		this.tableNames = new ArrayList<>();
	}

	@Override
	public Database table(String tableName) throws SQLException {
		if (connection == null) throw new SQLException("There is no connection");
		this.table = tableName;
		return null;
	}

	@Override
	public void connect() throws SQLException {
		if (connection != null) throw new SQLException("There is unclosed connection");
		String url = "jdbc:mysql:" + fileHandler.getDirectory();
		try {
			Class.forName("org.mysql.cj.jdbc.Driver");
			connection = DriverManager.getConnection(url);
		} catch (ClassNotFoundException exception) {
			Gangland.getInstance().getLogger().warning("Cannot find JDBC library.");
			exception.printStackTrace();
		}
	}

	@Override
	public void disconnect() {

	}

	@Override
	public void createTable(String... values) {

	}

	@Override
	public void deleteTable() {

	}

	@Override
	public Connection getConnection() {
		return null;
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public void setTableName(String newName) {

	}

	@Override
	public void addColumn(String name, String value) {

	}

	@Override
	public void insert(String... values) {

	}

	@Override
	public Object[] select(String row, String... columns) {
		return new Object[0];
	}

	@Override
	public void update(String row, String... values) {

	}

	@Override
	public int totalRows() {
		return 0;
	}

	@Override
	public void delete(String value) {

	}

	@Override
	public ResultSet executeQuery(String statement) {
		return null;
	}

	@Override
	public void executeUpdate(String statement) {

	}

	@Override
	public List<String> getTables() {
		return null;
	}

}
