package me.luckyraven.persistence.database.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Column}.
 *
 * <p>Pure Java — no database, no mocks.
 */
@DisplayName("Column - construction and type-inference contracts")
class ColumnTest {

	// ──────────────────────────────────────── Record components ──

	@Test
	@DisplayName("of(name, value) - stores name and value exactly as supplied")
	void of_storesNameAndValue() {
		Column col = Column.of("balance", 500.0);

		assertEquals("balance", col.name());
		assertEquals(500.0, col.value());
	}

	@Test
	@DisplayName("of(name, value, type) - stores all three components verbatim")
	void of_withExplicitType_storesAllComponents() {
		Column col = Column.of("uuid", "abc-123", Types.CHAR);

		assertEquals("uuid", col.name());
		assertEquals("abc-123", col.value());
		assertEquals(Types.CHAR, col.type());
	}

	// ────────────────────────────────────────── Type inference ──

	@Test
	@DisplayName("of(name, String) - infers VARCHAR")
	void of_string_inferredAsVarchar() {
		assertEquals(Types.VARCHAR, Column.of("name", "Alice").type());
	}

	@Test
	@DisplayName("of(name, Integer) - infers INTEGER")
	void of_integer_inferredAsInteger() {
		assertEquals(Types.INTEGER, Column.of("count", 42).type());
	}

	@Test
	@DisplayName("of(name, Long) - infers BIGINT")
	void of_long_inferredAsBigint() {
		assertEquals(Types.BIGINT, Column.of("timestamp_ms", 1_000_000L).type());
	}

	@Test
	@DisplayName("of(name, Double) - infers DOUBLE")
	void of_double_inferredAsDouble() {
		assertEquals(Types.DOUBLE, Column.of("balance", 99.9).type());
	}

	@Test
	@DisplayName("of(name, Float) - infers DOUBLE (DatabaseUtil maps Float → DOUBLE)")
	void of_float_inferredAsDouble() {
		assertEquals(Types.DOUBLE, Column.of("ratio", 0.5f).type());
	}

	@Test
	@DisplayName("of(name, Boolean) - infers BOOLEAN")
	void of_boolean_inferredAsBoolean() {
		assertEquals(Types.BOOLEAN, Column.of("active", true).type());
	}

	@Test
	@DisplayName("of(name, LocalDateTime) - infers TIMESTAMP")
	void of_localDateTime_inferredAsTimestamp() {
		assertEquals(Types.TIMESTAMP, Column.of("created_at", LocalDateTime.now()).type());
	}

	@Test
	@DisplayName("of(name, null) - produces Types.NULL without throwing")
	void of_nullValue_producesNullType() {
		Column col = Column.of("optional_field", null);

		assertNull(col.value());
		assertEquals(Types.NULL, col.type());
	}

	// ────────────────────────────────── Explicit type override ──

	@Test
	@DisplayName("of(name, value, type) - explicit CHAR overrides inferred VARCHAR for a String value")
	void of_explicitCharOverridesInferredVarchar() {
		Column inferred = Column.of("uuid", "abc-123");
		Column explicit = Column.of("uuid", "abc-123", Types.CHAR);

		assertEquals(Types.VARCHAR, inferred.type(), "Auto-inferred type should be VARCHAR");
		assertEquals(Types.CHAR, explicit.type(), "Explicit type should be CHAR");
	}

	// ──────────────────────────────────────── Equality (record) ──

	@Test
	@DisplayName("two Columns with identical components are equal")
	void equals_sameComponents_areEqual() {
		Column a = Column.of("score", 10, Types.INTEGER);
		Column b = Column.of("score", 10, Types.INTEGER);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	@DisplayName("two Columns that differ in type are not equal")
	void equals_differentTypes_notEqual() {
		Column varchar = Column.of("id", "x", Types.VARCHAR);
		Column charCol = Column.of("id", "x", Types.CHAR);

		assertNotEquals(varchar, charCol);
	}
}
