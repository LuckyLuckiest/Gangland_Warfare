package me.luckyraven.util;

import java.sql.Types;
import java.time.LocalDateTime;

public final class DatabaseUtil {

	private DatabaseUtil() { }

	/**
	 * Gets a column type.
	 *
	 * @param columnType the column type
	 *
	 * @return the column type
	 */
	public static int getColumnType(Class<?> columnType) {
		if (columnType == null) return Types.NULL;
		if (columnType.equals(Byte.class)) return Types.TINYINT;
		if (columnType.equals(Short.class)) return Types.SMALLINT;
		if (columnType.equals(Integer.class)) return Types.INTEGER;
		if (columnType.equals(Long.class)) return Types.BIGINT;
		if (columnType.equals(Float.class)) return Types.FLOAT;
		if (columnType.equals(Double.class)) return Types.DOUBLE;
		if (columnType.equals(Boolean.class)) return Types.BOOLEAN;
		if (columnType.equals(LocalDateTime.class)) return Types.TIMESTAMP;
		return Types.VARCHAR;
	}

	/**
	 * Gets the java equivalent data type of the database used data types.
	 *
	 * @param columnType the column type
	 *
	 * @return the java type
	 */
	public static Class<?> getJavaType(int columnType) {
		return switch (columnType) {
			case Types.TINYINT -> Byte.class;
			case Types.SMALLINT -> Short.class;
			case Types.INTEGER -> Integer.class;
			case Types.BIGINT -> Long.class;
			case Types.FLOAT, Types.REAL -> Float.class;
			case Types.DOUBLE -> Double.class;
			case Types.BOOLEAN, Types.BIT -> Boolean.class;
			case Types.DATE, Types.TIME, Types.TIMESTAMP -> LocalDateTime.class;
			case Types.NULL, Types.NUMERIC -> null;
			default -> String.class;
		};
	}

}
