package me.luckyraven.database.type;

import me.luckyraven.database.Database;
import me.luckyraven.file.FileManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class SQLite implements Database {

	private final JavaPlugin plugin;
	private final String     name;

	public SQLite(JavaPlugin plugin, String name) {
		this.plugin = plugin;
		this.name = name;
	}

	@Override
	public void initialize(FileManager fileManager) {

	}

	@Override
	public Database table(String tableName) throws SQLException {
		return this;
	}

	@Override
	public void connect() throws SQLException {

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
	public Database addColumn(String name, String value) {
		return this;
	}

	@Override
	public Database insert(String[] columns, Object[] values, int[] types) throws SQLException {
		return this;
	}

	@Override
	public Object[] select(String row, String... columns) {
		return new Object[0];
	}

	@Override
	public Database update(String row, String... values) {
		return this;
	}

	@Override
	public int totalRows() {
		return 0;
	}

	@Override
	public Database delete(String value) {

		return this;
	}

	@Override
	public ResultSet executeQuery(String statement) {
		return null;
	}

	@Override
	public Database executeUpdate(String statement) {
		return this;
	}

	@Override
	public List<String> getTables() {
		return null;
	}

}
