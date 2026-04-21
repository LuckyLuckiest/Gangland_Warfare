package me.luckyraven.persistence.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-dialect helpers for repository-level schema migrations — e.g. flipping a column from UNIQUE to part of a
 * composite primary key. Each method is dialect-aware and selects the right introspection query or migration path based
 * on {@link DatabaseHandler#getType()}. Repositories call these from their {@code migrateSchema()} override instead of
 * hand-rolling pragma/information_schema queries per fix.
 *
 * <p>For SQLite, schema changes that alter constraints require the copy-drop-rename dance — {@code ALTER TABLE}
 * can't flip a primary key in place. {@link #rebuildSqliteTable} encapsulates that pattern so callers only provide the
 * new table DDL and the columns to preserve.
 */
public final class SchemaMigrations {

	private SchemaMigrations() {
	}

	/**
	 * Returns true when the given column participates in the table's primary key. False if the column isn't a PK, the
	 * table doesn't exist, or the dialect is unsupported.
	 */
	public static boolean isColumnInPrimaryKey(Connection conn, int dbType, String tableName, String columnName)
			throws SQLException {
		if (conn == null || tableName == null || columnName == null) return false;

		switch (dbType) {
			case DatabaseHandler.SQLITE: {
				try (Statement stmt = conn.createStatement();
				     ResultSet rs = stmt.executeQuery("PRAGMA table_info('" + tableName + "')")) {
					while (rs.next()) {
						String name = rs.getString("name");
						int    pk   = rs.getInt("pk");
						if (columnName.equalsIgnoreCase(name) && pk > 0) return true;
					}
				}
				return false;
			}
			case DatabaseHandler.MYSQL: {
				try (Statement stmt = conn.createStatement();
				     ResultSet rs = stmt.executeQuery(
							 "SELECT COLUMN_NAME FROM information_schema.STATISTICS " +
						     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
						     "AND INDEX_NAME = 'PRIMARY'")) {
					while (rs.next()) {
						if (columnName.equalsIgnoreCase(rs.getString(1))) return true;
					}
				}
				return false;
			}
			default:
				return false;
		}
	}

	/**
	 * Returns true when the given column is covered by a UNIQUE constraint or unique index (not the primary key).
	 * Useful for detecting legacy schemas where a column that should be part of a composite PK was instead marked
	 * UNIQUE.
	 */
	public static boolean hasUniqueIndex(Connection conn, int dbType, String tableName, String columnName)
			throws SQLException {
		if (conn == null || tableName == null || columnName == null) return false;

		switch (dbType) {
			case DatabaseHandler.SQLITE: {
				// PRAGMA index_list returns every index on the table with a 'unique' flag. For each unique index we
				// peek at index_info to see which column it covers.
				List<String> uniqueIndexes = new ArrayList<>();
				try (Statement stmt = conn.createStatement();
				     ResultSet rs = stmt.executeQuery("PRAGMA index_list('" + tableName + "')")) {
					while (rs.next()) {
						if (rs.getInt("unique") == 1) uniqueIndexes.add(rs.getString("name"));
					}
				}
				for (String indexName : uniqueIndexes) {
					try (Statement stmt = conn.createStatement();
					     ResultSet rs = stmt.executeQuery("PRAGMA index_info('" + indexName + "')")) {
						while (rs.next()) {
							if (columnName.equalsIgnoreCase(rs.getString("name"))) return true;
						}
					}
				}
				return false;
			}
			case DatabaseHandler.MYSQL: {
				try (Statement stmt = conn.createStatement();
				     ResultSet rs = stmt.executeQuery(
							 "SELECT INDEX_NAME FROM information_schema.STATISTICS " +
						     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
						     "AND COLUMN_NAME = '" + columnName + "' " +
						     "AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'")) {
					if (rs.next()) return true;
				}
				return false;
			}
			default:
				return false;
		}
	}

	/**
	 * Rebuilds a SQLite table in-place under the same name: creates {@code tableName_migration}, copies the specified
	 * columns over, drops the original, and renames the replacement. Wrapped in a transaction so a mid-flight failure
	 * rolls back cleanly. Use when you need to change a PK or constraint — SQLite's {@code ALTER TABLE} is too narrow
	 * for that.
	 *
	 * @param conn an open connection
	 * @param tableName the existing table to rebuild
	 * @param newTableDDL the full {@code CREATE TABLE <tableName>_migration (...)} statement for the replacement
	 * @param columnsToCopy columns to preserve from the old table; order matches both sides of the INSERT ... SELECT
	 */
	public static void rebuildSqliteTable(Connection conn, String tableName, String newTableDDL,
	                                      String... columnsToCopy) throws SQLException {
		if (conn == null) throw new SQLException("No database connection");

		String tempName = tableName + "_migration";
		String columns  = String.join(", ", columnsToCopy);

		boolean prevAutoCommit = conn.getAutoCommit();
		try (Statement stmt = conn.createStatement()) {
			conn.setAutoCommit(false);
			stmt.execute(newTableDDL);
			stmt.execute("INSERT OR IGNORE INTO " + tempName + " (" + columns + ") " +
			             "SELECT " + columns + " FROM " + tableName);
			stmt.execute("DROP TABLE " + tableName);
			stmt.execute("ALTER TABLE " + tempName + " RENAME TO " + tableName);
			conn.commit();
		} catch (SQLException exception) {
			try {
				conn.rollback();
			} catch (SQLException ignored) { }
			throw exception;
		} finally {
			conn.setAutoCommit(prevAutoCommit);
		}
	}

}
