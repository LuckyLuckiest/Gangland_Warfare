package me.luckyraven.persistence.database.query;

import me.luckyraven.util.utilities.DatabaseUtil;

import java.sql.Types;

/**
 * Pairs a column name with its value and JDBC type.
 *
 * <p>Use {@link #of(String, Object)} to have the JDBC type inferred automatically from the
 * value's runtime class via {@link DatabaseUtil#getColumnType(Class)}. Use {@link #of(String, Object, int)} when you
 * need an explicit type (e.g. a {@link java.util.UUID} stored as {@link Types#CHAR}).
 */
public record Column(String name, Object value, int type) {

	/**
	 * Creates a Column whose JDBC type is inferred from {@code value}'s runtime class. A {@code null} value produces
	 * {@link Types#NULL}.
	 */
	public static Column of(String name, Object value) {
		int type = value != null ? DatabaseUtil.getColumnType(value.getClass()) : Types.NULL;
		return new Column(name, value, type);
	}

	/**
	 * Creates a Column with an explicit JDBC type constant from {@link Types}.
	 */
	public static Column of(String name, Object value, int type) {
		return new Column(name, value, type);
	}
}