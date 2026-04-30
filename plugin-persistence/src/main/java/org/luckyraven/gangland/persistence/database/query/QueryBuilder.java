package org.luckyraven.gangland.persistence.database.query;

import org.luckyraven.gangland.persistence.database.Database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Fluent query builder that wraps {@link Database} to replace raw parallel arrays with named column-value pairs whose
 * JDBC types are inferred automatically.
 *
 * <pre>{@code
 * QueryBuilder qb = QueryBuilder.on(database, "users");
 *
 * // INSERT
 * qb.insert()
 *   .set("name", "Alice")
 *   .set("balance", 500.0)
 *   .execute();
 *
 * // SELECT – single row
 * Object[] row = qb.select("name", "balance")
 *   .where("uuid", uuid.toString())
 *   .executeOne();
 *
 * // SELECT – all rows (optionally filtered)
 * List<Object[]> rows = qb.select("*")
 *   .where("active", true)
 *   .executeAll();
 *
 * // UPDATE
 * qb.update()
 *   .set("balance", 600.0)
 *   .where("uuid", uuid.toString())
 *   .execute();
 *
 * // DELETE – multi-condition
 * qb.delete()
 *   .where("uuid", uuid.toString())
 *   .where("active", false)
 *   .execute();
 * }</pre>
 */
public final class QueryBuilder {

	private final Database database;
	private final String   tableName;

	private QueryBuilder(Database database, String tableName) {
		this.database  = database;
		this.tableName = tableName;
	}

	/**
	 * Creates a builder scoped to the given table on the given database connection.
	 */
	public static QueryBuilder on(Database database, String tableName) {
		return new QueryBuilder(database, tableName);
	}

	public Insert insert() {
		return new Insert(this);
	}

	public Select select(String... columns) {
		return new Select(this, columns);
	}

	public Update update() {
		return new Update(this);
	}

	public Delete delete() {
		return new Delete(this);
	}

	// ---- INSERT -----------------------------------------------------------------------

	public static final class Insert {

		private final QueryBuilder qb;
		private final List<Column> cols = new ArrayList<>();

		Insert(QueryBuilder qb) {
			this.qb = qb;
		}

		/**
		 * Adds a column-value pair; the JDBC type is inferred from the value's class.
		 */
		public Insert set(String name, Object value) {
			cols.add(Column.of(name, value));
			return this;
		}

		/**
		 * Adds a pre-built {@link Column} (use when you need an explicit JDBC type).
		 */
		public Insert set(Column column) {
			cols.add(column);
			return this;
		}

		public void execute() throws SQLException {
			if (cols.isEmpty()) return;
			String[] names  = cols.stream().map(Column::name).toArray(String[]::new);
			Object[] values = cols.stream().map(Column::value).toArray();
			int[]    types  = cols.stream().mapToInt(Column::type).toArray();
			qb.database.table(qb.tableName).insert(names, values, types);
		}
	}

	// ---- SELECT -----------------------------------------------------------------------

	public static final class Select {

		private final QueryBuilder qb;
		private final String[]     selectCols;
		private final List<Column> conditions = new ArrayList<>();

		Select(QueryBuilder qb, String[] selectCols) {
			this.qb         = qb;
			this.selectCols = selectCols;
		}

		/**
		 * Adds an equality condition for the WHERE clause.
		 */
		public Select where(String name, Object value) {
			conditions.add(Column.of(name, value));
			return this;
		}

		/**
		 * Adds a pre-built {@link Column} condition (use for explicit JDBC types).
		 */
		public Select where(Column column) {
			conditions.add(column);
			return this;
		}

		/**
		 * Executes and returns the raw result array from {@link Database#select}. For a single matching row this array
		 * holds one value per requested column.
		 */
		public Object[] executeOne() throws SQLException {
			String   where = buildWhereClause();
			Object[] ph    = conditions.stream().map(Column::value).toArray();
			int[]    types = conditions.stream().mapToInt(Column::type).toArray();
			return qb.database.table(qb.tableName).select(where, ph, types, selectCols);
		}

		/**
		 * Returns every matching row as a separate {@code Object[]}. Delegates to
		 * {@link Database#selectAll(String, Object[], int[])} when conditions are present. When there are no
		 * conditions, uses an explicit column list (if specific columns were requested) so that the returned row
		 * indices are independent of the physical DB column order.
		 */
		public List<Object[]> executeAll() throws SQLException {
			if (conditions.isEmpty()) {
				boolean isStar = selectCols.length == 1 && "*".equals(selectCols[0]);
				if (!isStar) {
					return qb.database.table(qb.tableName).selectAll(selectCols);
				}
				return qb.database.table(qb.tableName).selectAll();
			}
			String   where = buildWhereClause();
			Object[] ph    = conditions.stream().map(Column::value).toArray();
			int[]    types = conditions.stream().mapToInt(Column::type).toArray();
			return qb.database.table(qb.tableName).selectAll(where, ph, types);
		}

		private String buildWhereClause() {
			if (conditions.isEmpty()) return "";
			StringJoiner sj = new StringJoiner(" AND ");
			for (Column c : conditions) sj.add(c.name() + " = ?");
			return sj.toString();
		}
	}

	// ---- UPDATE -----------------------------------------------------------------------

	public static final class Update {

		private final QueryBuilder qb;
		private final List<Column> sets       = new ArrayList<>();
		private final List<Column> conditions = new ArrayList<>();

		Update(QueryBuilder qb) {
			this.qb = qb;
		}

		/**
		 * Specifies a column to update; JDBC type is inferred from the value's class.
		 */
		public Update set(String name, Object value) {
			sets.add(Column.of(name, value));
			return this;
		}

		/**
		 * Adds a pre-built {@link Column} to update (use for explicit JDBC types).
		 */
		public Update set(Column column) {
			sets.add(column);
			return this;
		}

		/**
		 * Adds an equality condition for the WHERE clause.
		 */
		public Update where(String name, Object value) {
			conditions.add(Column.of(name, value));
			return this;
		}

		/**
		 * Adds a pre-built {@link Column} condition (use for explicit JDBC types).
		 */
		public Update where(Column column) {
			conditions.add(column);
			return this;
		}

		public void execute() throws SQLException {
			if (sets.isEmpty()) return;
			String   where      = buildWhereClause();
			Object[] wherePh    = conditions.stream().map(Column::value).toArray();
			int[]    whereTypes = conditions.stream().mapToInt(Column::type).toArray();
			String[] setCols    = sets.stream().map(Column::name).toArray(String[]::new);
			Object[] setVals    = sets.stream().map(Column::value).toArray();
			int[]    setTypes   = sets.stream().mapToInt(Column::type).toArray();
			qb.database.table(qb.tableName).update(where, wherePh, whereTypes, setCols, setVals, setTypes);
		}

		private String buildWhereClause() {
			if (conditions.isEmpty()) return "";
			StringJoiner sj = new StringJoiner(" AND ");
			for (Column c : conditions) sj.add(c.name() + " = ?");
			return sj.toString();
		}
	}

	// ---- DELETE -----------------------------------------------------------------------

	public static final class Delete {

		private final QueryBuilder qb;
		private final List<Column> conditions = new ArrayList<>();

		Delete(QueryBuilder qb) {
			this.qb = qb;
		}

		/**
		 * Adds an equality condition for the WHERE clause.
		 */
		public Delete where(String name, Object value) {
			conditions.add(Column.of(name, value));
			return this;
		}

		/**
		 * Adds a pre-built {@link Column} condition (use for explicit JDBC types).
		 */
		public Delete where(Column column) {
			conditions.add(column);
			return this;
		}

		public void execute() throws SQLException {
			Database scoped = qb.database.table(qb.tableName);
			if (conditions.isEmpty()) {
				scoped.delete("", null, 0);
				return;
			}
			if (conditions.size() == 1) {
				Column c = conditions.getFirst();
				scoped.delete(c.name(), c.value(), c.type());
				return;
			}
			// Multi-condition: use the WHERE-clause overload
			String   where = buildWhereClause();
			Object[] ph    = conditions.stream().map(Column::value).toArray();
			int[]    types = conditions.stream().mapToInt(Column::type).toArray();
			scoped.delete(where, ph, types);
		}

		private String buildWhereClause() {
			StringJoiner sj = new StringJoiner(" AND ");
			for (Column c : conditions) sj.add(c.name() + " = ?");
			return sj.toString();
		}
	}
}
