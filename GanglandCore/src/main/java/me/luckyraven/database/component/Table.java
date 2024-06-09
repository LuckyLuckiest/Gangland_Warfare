package me.luckyraven.database.component;

import lombok.Getter;
import me.luckyraven.database.Database;

import java.sql.SQLException;
import java.util.*;

@Getter
public abstract class Table<T> {

	private final String                    name;
	private final Map<String, Attribute<?>> attributes;

	public Table(String name) {
		this.name       = name;
		this.attributes = new LinkedHashMap<>();
	}

	public abstract Object[] getData(T data);

	// standard would be
	// "search" -> the string that would be used to search with
	// "info" -> the Object[] which is used to substitute in the search
	// "type" -> the int[] data type of the info
	// "index" -> ignores the indexes from getData method returned values
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

	public void insertTableQuery(Database database, T data) throws SQLException {
		String[] columns         = getColumns().toArray(String[]::new);
		int[]    columnsDataType = attributes.values().stream().mapToInt(Attribute::getType).toArray();
		Database config          = database.table(name);

		config.insert(columns, getData(data), columnsDataType);
	}

	public void updateTableQuery(Database database, T data) throws SQLException {
		Map<String, Object> search             = searchCriteria(data);
		Object[]            allData            = getData(data);
		List<String>        columnsTemp        = getColumns().stream().toList();
		int[]               indexes            = (int[]) search.get("index");
		List<String>        colTemp            = new ArrayList<>();
		List<Object>        objectsTemp        = new ArrayList<>();
		int[]               extractedDataTypes = attributes.values().stream().mapToInt(Attribute::getType).toArray();
		List<Integer>       dataTypesTemp      = new ArrayList<>();

		for (int i = 0; i < columnsTemp.size(); i++) {
			int finalI = i;
			if (Arrays.stream(indexes).anyMatch(value -> value == finalI)) continue;

			colTemp.add(columnsTemp.get(i));
			objectsTemp.add(allData[i]);
			dataTypesTemp.add(extractedDataTypes[i]);
		}

		int[] dataTypes = new int[dataTypesTemp.size()];

		for (int i = 0; i < dataTypes.length; i++)
			 dataTypes[i] = dataTypesTemp.get(i);

		Database config = database.table(name);

		config.update((String) search.get("search"), (Object[]) search.get("info"), (int[]) search.get("type"),
					  colTemp.toArray(String[]::new), objectsTemp.toArray(), dataTypes);
	}

	protected void addAttribute(Attribute<?> attribute) {
		attributes.put(attribute.getName(), attribute);
	}

}
