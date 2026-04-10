package me.luckyraven.persistence.database.component;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.persistence.database.Database;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@CustomLog
@Getter
public abstract class Table<T> {

	private final String                    name;
	private final Map<String, Attribute<?>> attributes;

	public Table(String name) {
		this.name       = name;
		this.attributes = new LinkedHashMap<>();
	}

	public abstract Object[] getData(T data);

	/**
	 * Standard would be:
	 * <p>"search" -> the string that would be used to search with</p>
	 * <p>"info" -> the Object[] which is used to substitute in the search</p>
	 * <p>"type" -> the int[] data type of the info</p>
	 * <p>"index" -> ignores the indexes from getData method returned values</p>
	 */
	public abstract Map<String, Object> searchCriteria(T data);

	public Attribute<?> get(String column) {
		return attributes.get(column.toLowerCase());
	}

	public Set<String> getColumns() {
		return attributes.keySet();
	}

	public Map<String, Attribute<?>> getAttributes() {
		return Collections.unmodifiableMap(attributes);
	}

	public String[] createTableQuery(Database database) {
		List<String> lines = new ArrayList<>();

		// iterate over each attribute
		for (Map.Entry<String, Attribute<?>> entry : attributes.entrySet()) {
			// need the sql query to be constructed (build)
			StringBuilder value = new StringBuilder();
			// get the key (column name) and the attribute (data information)
			String       attributeName = entry.getKey();
			Attribute<?> attribute     = entry.getValue();

			value.append(attributeName).append(" ");
			value.append(database.getStringDataType(attribute.getType(), attribute.getSize()));

			if (attribute.isPrimaryKey()) value.append(" PRIMARY KEY");
			else if (attribute.isUnique()) value.append(" UNIQUE");
			if (attribute.isCanBeNull()) value.append(" NULL");
			else value.append(" NOT NULL");

			Object defaultValue = attribute.getDefaultValue();
			if (defaultValue != null) {
				value.append(" DEFAULT ");

				if (defaultValue instanceof String) value.append("'").append(defaultValue).append("'");
				else value.append(defaultValue);
			}

			value.trimToSize();
			lines.add(value.toString());
		}

		for (Map.Entry<String, Attribute<?>> entry : getAttributes().entrySet()) {
			Attribute<?> attribute = entry.getValue();

			if (attribute.getForeignKey() == null) continue;

			lines.add(
					"FOREIGN KEY (" + attribute.getName() + ") REFERENCES " + attribute.getAssociatedTable().getName() +
					"(" + attribute.getForeignKey().getName() + ")");
		}

		return lines.toArray(String[]::new);
	}

	public synchronized void insertTableQuery(Database database, T data) throws SQLException {
		String[] columns = getColumns().toArray(String[]::new);
		Object[] allData = getData(data);
		int[] columnsDataType = attributes.values()
				.stream().mapToInt(Attribute::getType).toArray();
		Database config = database.table(name);

		if (allData == null || allData.length == 0) return;

		config.insert(columns, allData, columnsDataType);
	}

	public synchronized void updateTableQuery(Database database, T data) throws SQLException {
		Map<String, Object> search  = searchCriteria(data);
		Object[]            allData = getData(data);

		// there is no data to work with
		if (allData == null || allData.length == 0) return;

		List<String>  columnsTemp   = getColumns().stream().toList();
		List<String>  colTemp       = new ArrayList<>();
		List<Object>  objectsTemp   = new ArrayList<>();
		List<Integer> dataTypesTemp = new ArrayList<>();

		int[]        indexes  = (int[]) search.get("index");
		Set<Integer> indexSet = Arrays.stream(indexes).boxed().collect(Collectors.toSet());
		int[] extractedDataTypes = attributes.values()
				.stream().mapToInt(Attribute::getType).toArray();

		boolean includedNonPrimaryKeyColumn = false;

		for (int i = 0; i < columnsTemp.size(); i++) {
			if (indexSet.contains(i)) continue;

			colTemp.add(columnsTemp.get(i));
			objectsTemp.add(allData[i]);
			dataTypesTemp.add(extractedDataTypes[i]);

			includedNonPrimaryKeyColumn = true;
		}

		if (!includedNonPrimaryKeyColumn && !columnsTemp.isEmpty()) {
			List<Attribute<?>> attr = attributes.values()
					.stream().toList();

			for (int i = 0; i < columnsTemp.size(); i++) {
				// ignore primary key
				boolean isPrimaryKey = attr.get(i).isPrimaryKey();

				if (isPrimaryKey) continue;

				colTemp.add(columnsTemp.get(i));
				objectsTemp.add(allData[i]);
				dataTypesTemp.add(extractedDataTypes[i]);
			}
		}

		int[]    dataTypes = dataTypesTemp.stream().mapToInt(Integer::intValue).toArray();
		Database config    = database.table(name);

		config.update((String) search.get("search"), (Object[]) search.get("info"), (int[]) search.get("type"),
		              colTemp.toArray(String[]::new), objectsTemp.toArray(), dataTypes);
	}

	/**
	 * Persists a single row using an UPSERT statement (INSERT … ON CONFLICT DO UPDATE for SQLite, INSERT … ON DUPLICATE
	 * KEY UPDATE for MySQL). Eliminates the need for a separate existence check before saving.
	 *
	 * @param database an unscoped {@link Database} instance (the method scopes it internally)
	 * @param data the entity to persist
	 *
	 * @throws SQLException if the upsert query fails or no primary key is defined on this table
	 */
	public synchronized void upsertTableQuery(Database database, T data) throws SQLException {
		Object[] values = getData(data);
		if (values == null || values.length == 0) return;

		String[]   allColumns      = getColumns().toArray(String[]::new);
		String[]   conflictColumns = getPrimaryKeyColumns();
		Database   scoped          = database.table(name);
		String     sql             = scoped.buildUpsertQuery(conflictColumns, allColumns);
		Connection conn            = database.getConnection();
		if (conn == null) throw new SQLException("No database connection");

		int[] columnsDataType = attributes.values()
				.stream().mapToInt(Attribute::getType).toArray();

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			database.preparePlaceholderStatements(stmt, values, columnsDataType, 0);
			stmt.executeUpdate();
		}
	}

	/**
	 * Persists an entire collection of rows in a single JDBC batch using UPSERT statements. Rows are batched inside a
	 * transaction for atomicity and performance — the database processes all rows in one round-trip instead of issuing
	 * individual queries per row.
	 *
	 * @param database an unscoped {@link Database} instance (the method scopes it internally)
	 * @param data the entities to persist; an empty or {@code null} collection is a no-op
	 *
	 * @throws SQLException if the batch execution fails or no primary key is defined on this table
	 */
	public synchronized void batchUpsertTableQuery(Database database, Collection<? extends T> data) throws
			SQLException {
		if (data == null || data.isEmpty()) return;

		String[]   allColumns      = getColumns().toArray(String[]::new);
		String[]   conflictColumns = getPrimaryKeyColumns();
		Database   scoped          = database.table(name);
		String     sql             = scoped.buildUpsertQuery(conflictColumns, allColumns);
		Connection conn            = database.getConnection();
		if (conn == null) throw new SQLException("No database connection");

		int[] columnsDataType = attributes.values()
				.stream().mapToInt(Attribute::getType).toArray();

		boolean prevAutoCommit = conn.getAutoCommit();
		try {
			conn.setAutoCommit(false);
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				for (T row : data) {
					Object[] values = getData(row);
					if (values == null || values.length == 0) continue;

					database.preparePlaceholderStatements(stmt, values, columnsDataType, 0);
					stmt.addBatch();
				}
				stmt.executeBatch();
			}
			conn.commit();
		} catch (SQLException e) {
			try {
				conn.rollback();
			} catch (SQLException ignored) { }
			throw e;
		} finally {
			conn.setAutoCommit(prevAutoCommit);
		}
	}

	/**
	 * Validates and synchronizes the live database schema against this table's attribute definitions.
	 * <p>
	 * Columns present in the definition but absent from the database are added; columns present in the database but
	 * absent from the definition are dropped. This runs after {@code createTable} so it is always safe to call on an
	 * existing table - a freshly created table will produce no diff.
	 * <p>
	 * When adding a NOT NULL column that carries no explicit default, a type-appropriate fallback default is applied
	 * automatically so that existing rows are not left with an illegal null.
	 *
	 * @param database an unscoped {@link Database} instance (the method scopes it internally via
	 *        {@link Database#table(String)})
	 *
	 * @throws SQLException if adding a column fails (drop failures are warned and swallowed)
	 */
	public void validateSchema(Database database) throws SQLException {
		Database tableDb = database.table(name);

		// Fetch the column names that actually exist in the database (lowercased for consistent comparison)
		List<String> actualColumns = tableDb.getColumns()
				.stream().map(String::toLowerCase).toList();

		// 1. Add columns that are defined but missing from the database
		for (Map.Entry<String, Attribute<?>> entry : attributes.entrySet()) {
			String       col  = entry.getKey(); // already lowercase per Attribute constructor
			Attribute<?> attr = entry.getValue();

			if (actualColumns.contains(col)) continue;

			String colDef = buildColumnDefinition(database, attr);
			log.info("Table '{}': adding missing column '{}'", name, col);
			tableDb.addColumn(col, colDef);
		}

		// 2. Drop columns that exist in the database but are no longer in the definition
		for (String actual : actualColumns) {
			if (attributes.containsKey(actual)) continue;

			if (!database.isValidIdentifier(actual)) {
				log.warn("Table '{}': skipping drop of '{}' - identifier contains unsafe characters", name, actual);
				continue;
			}

			log.info("Table '{}': dropping obsolete column '{}'", name, actual);
			try {
				tableDb.executeUpdate("ALTER TABLE " + name + " DROP COLUMN " + actual);
			} catch (SQLException e) {
				log.warn("Table '{}': could not drop column '{}': {}", name, actual, e.getMessage());
			}
		}

		// 3. Fix nullability mismatches on existing columns
		String dbProduct = database.getConnection().getMetaData().getDatabaseProductName().toLowerCase();
		if (dbProduct.contains("sqlite")) {
			fixNullabilityMismatchesSQLite(database);
		} else {
			fixNullabilityMismatchesMySQL(database);
		}
	}

	/**
	 * Selects all rows from the backing database table and returns them with columns in the same order as this table's
	 * attribute definition.
	 * <p>
	 * This is the safe alternative to calling {@code database.table(name).selectAll()} directly. The plain
	 * {@link Database#selectAll()} issues {@code SELECT *}, which returns values in the physical DB column order. When
	 * {@link #validateSchema} adds a new column via {@code ALTER TABLE ADD COLUMN}, that column is physically appended
	 * at the end regardless of its position in the attribute {@link java.util.LinkedHashMap}. Callers reading the
	 * result by positional index would therefore receive values in the wrong slots.
	 * <p>
	 * This method issues {@code SELECT col1, col2, ...} with an explicit column list derived from {@link #getColumns()}
	 * (which preserves insertion/definition order), so the returned {@code Object[]} indices always match the order in
	 * which attributes were registered via {@link #addAttribute}.
	 *
	 * @param database an unscoped {@link Database} instance (the method scopes it internally)
	 *
	 * @return list of rows; each {@code Object[]} has values positionally matching the attribute definition order
	 *
	 * @throws SQLException if the query fails
	 */
	public List<Object[]> selectAllTableQuery(Database database) throws SQLException {
		String[] orderedColumns = getColumns().toArray(String[]::new);
		return database.table(name).selectAll(orderedColumns);
	}

	protected Map<String, Object> createSearchCriteria(String searchQuery, Object[] queryPlaceholder,
	                                                   int[] queryDataTypes, int[] ignoredIndexes) {
		return Map.of("search", searchQuery, "info", queryPlaceholder, "type", queryDataTypes, "index", ignoredIndexes);
	}

	protected void addAttribute(Attribute<?> attribute) {
		attributes.put(attribute.getName(), attribute);
	}

	private String[] getPrimaryKeyColumns() {
		return attributes.entrySet()
				.stream()
				.filter(e -> e.getValue().isPrimaryKey())
				.map(Map.Entry::getKey)
				.toArray(String[]::new);
	}

	/**
	 * Detects nullability mismatches between the live SQLite schema and this table's attribute definitions. If any
	 * mismatch is found, the table is recreated via rename-create-copy-drop so that the correct constraints apply.
	 */
	private void fixNullabilityMismatchesSQLite(Database database) throws SQLException {
		Map<String, Boolean> dbCanBeNull = new HashMap<>();
		try (PreparedStatement stmt = database.getConnection().prepareStatement("PRAGMA table_info(" + name + ")");
		     ResultSet rs = stmt.executeQuery()) {
			while (rs.next()) {
				String  col     = rs.getString("name").toLowerCase();
				boolean notNull = rs.getInt("notnull") == 1;
				dbCanBeNull.put(col, !notNull);
			}
		}

		for (Map.Entry<String, Attribute<?>> entry : attributes.entrySet()) {
			Boolean current = dbCanBeNull.get(entry.getKey());
			if (current == null) continue;
			if (current != entry.getValue().isCanBeNull()) {
				log.info("Table '{}': nullability mismatch on '{}' (db={}, def={}) - recreating table",
				         name, entry.getKey(),
				         current ? "NULL" : "NOT NULL",
				         entry.getValue().isCanBeNull() ? "NULL" : "NOT NULL");
				recreateTableSQLite(database);
				return;
			}
		}
	}

	/**
	 * Recreates a SQLite table in-place to apply schema changes that SQLite does not support via ALTER TABLE (e.g.
	 * nullability). Uses the rename-create-copy-drop pattern inside a transaction.
	 */
	private void recreateTableSQLite(Database database) throws SQLException {
		String     tmpName   = name + "_tmp";
		Connection conn      = database.getConnection();
		String     cols      = String.join(", ", attributes.keySet());
		String[]   colDefs   = createTableQuery(database);
		String     createSql = "CREATE TABLE " + name + " (" + String.join(", ", colDefs) + ")";

		// Capture the current FK enforcement state so it can be restored exactly after migration.
		// SQLite defaults to OFF; unconditionally enabling it after the migration would cause FK
		// violations on subsequent saves if referenced rows were not yet persisted.
		int prevFk = 0;
		try (PreparedStatement fkQuery = conn.prepareStatement("PRAGMA foreign_keys");
		     ResultSet fkRs = fkQuery.executeQuery()) {
			if (fkRs.next()) prevFk = fkRs.getInt(1);
		}

		database.executeUpdate("PRAGMA foreign_keys = OFF");
		conn.setAutoCommit(false);
		try {
			database.executeUpdate("DROP TABLE IF EXISTS " + tmpName);
			database.executeUpdate("ALTER TABLE " + name + " RENAME TO " + tmpName);
			database.executeUpdate(createSql);
			database.executeUpdate("INSERT INTO " + name + " (" + cols + ") SELECT " + cols + " FROM " + tmpName);
			database.executeUpdate("DROP TABLE " + tmpName);
			conn.commit();
			log.info("Table '{}': recreated successfully to apply nullability changes", name);
		} catch (SQLException e) {
			conn.rollback();
			throw e;
		} finally {
			conn.setAutoCommit(true);
			database.executeUpdate("PRAGMA foreign_keys = " + prevFk);
		}
	}

	/**
	 * Fixes nullability mismatches on a MySQL table by issuing {@code ALTER TABLE … MODIFY COLUMN} for each column
	 * whose nullability in the live schema differs from the attribute definition.
	 */
	private void fixNullabilityMismatchesMySQL(Database database) throws SQLException {
		String infoQuery = "SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS " +
		                   "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
		try (PreparedStatement stmt = database.getConnection().prepareStatement(infoQuery)) {
			stmt.setString(1, name);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					String       col        = rs.getString("COLUMN_NAME").toLowerCase();
					boolean      dbNullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
					Attribute<?> attr       = attributes.get(col);
					if (attr == null || dbNullable == attr.isCanBeNull()) continue;

					String colType = database.getStringDataType(attr.getType(), attr.getSize());
					String nullStr = attr.isCanBeNull() ? "NULL" : "NOT NULL";
					log.info("Table '{}': altering column '{}' nullability to {}", name, col, nullStr);
					database.executeUpdate(
							"ALTER TABLE " + name + " MODIFY COLUMN " + col + " " + colType + " " + nullStr);
				}
			}
		}
	}

	/**
	 * Builds the SQL column definition string (type + nullability + default) used when adding a column to an existing
	 * table via {@code ALTER TABLE ... ADD COLUMN}.
	 */
	private String buildColumnDefinition(Database database, Attribute<?> attribute) {
		StringBuilder def = new StringBuilder(database.getStringDataType(attribute.getType(), attribute.getSize()));

		if (attribute.isCanBeNull()) {
			def.append(" NULL");
		} else {
			def.append(" NOT NULL");
		}

		Object defaultValue = attribute.getDefaultValue();
		if (defaultValue != null) {
			def.append(" DEFAULT ");
			if (defaultValue instanceof String) def.append("'").append(defaultValue).append("'");
			else def.append(defaultValue);
		} else if (!attribute.isCanBeNull()) {
			// A NOT NULL column added to an existing table requires a DEFAULT so existing rows are valid
			def.append(" DEFAULT ").append(inferDefault(attribute.getType()));
		}

		return def.toString();
	}

	/**
	 * Returns a safe SQL literal default for a given JDBC type, used when a NOT NULL column is added to an existing
	 * table that has no explicit default value configured.
	 */
	private String inferDefault(int sqlType) {
		return switch (sqlType) {
			case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> "''";
			default -> "0";
		};
	}

}
