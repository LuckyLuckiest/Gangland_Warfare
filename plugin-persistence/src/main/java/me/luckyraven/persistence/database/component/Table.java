package me.luckyraven.persistence.database.component;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.persistence.database.Database;

import java.sql.SQLException;
import java.sql.Types;
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
	 * Validates and synchronizes the live database schema against this table's attribute definitions.
	 * <p>
	 * Columns present in the definition but absent from the database are added; columns present in the database but
	 * absent from the definition are dropped. This runs after {@code createTable} so it is always safe to call on an
	 * existing table — a freshly created table will produce no diff.
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
				log.warn("Table '{}': skipping drop of '{}' — identifier contains unsafe characters", name, actual);
				continue;
			}

			log.info("Table '{}': dropping obsolete column '{}'", name, actual);
			try {
				tableDb.executeUpdate("ALTER TABLE " + name + " DROP COLUMN " + actual);
			} catch (SQLException e) {
				log.warn("Table '{}': could not drop column '{}': {}", name, actual, e.getMessage());
			}
		}
	}

	protected Map<String, Object> createSearchCriteria(String searchQuery, Object[] queryPlaceholder,
													   int[] queryDataTypes, int[] ignoredIndexes) {
		return Map.of("search", searchQuery, "info", queryPlaceholder, "type", queryDataTypes, "index", ignoredIndexes);
	}

	protected void addAttribute(Attribute<?> attribute) {
		attributes.put(attribute.getName(), attribute);
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
