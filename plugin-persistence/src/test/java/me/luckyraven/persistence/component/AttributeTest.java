package me.luckyraven.persistence.component;

import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.support.TestTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Attribute}.
 *
 * <p>These are pure Java unit tests - no database, no Bukkit, no mocks.
 */
@DisplayName("Attribute - construction and property contracts")
class AttributeTest {

	// ─────────────────────────────────────────────────────── Name ──

	@Test
	@DisplayName("name is stored in lowercase regardless of input case")
	void name_isStoredLowercase() {
		Attribute<String> attr = new Attribute<>("MyColumn", false, String.class);
		assertEquals("mycolumn", attr.getName());
	}

	@Test
	@DisplayName("name with mixed case and underscore is lowercased")
	void name_withUnderscore_isLowercased() {
		Attribute<String> attr = new Attribute<>("Gang_Name", false, String.class);
		assertEquals("gang_name", attr.getName());
	}

	// ──────────────────────────────────────────────────────── Size ──

	@Test
	@DisplayName("String class defaults to size 255")
	void size_stringClass_defaults255() {
		Attribute<String> attr = new Attribute<>("col", false, String.class);
		assertEquals(255, attr.getSize());
	}

	@Test
	@DisplayName("UUID class defaults to size 36")
	void size_uuidClass_defaults36() {
		Attribute<UUID> attr = new Attribute<>("col", false, UUID.class);
		assertEquals(36, attr.getSize());
	}

	@Test
	@DisplayName("Integer class defaults to size 0")
	void size_integerClass_defaultsZero() {
		Attribute<Integer> attr = new Attribute<>("col", false, Integer.class);
		assertEquals(0, attr.getSize());
	}

	@Test
	@DisplayName("explicit size overrides the class default")
	void size_explicitSize_overridesDefault() {
		Attribute<String> attr = new Attribute<>("col", false, 64, String.class);
		assertEquals(64, attr.getSize());
	}

	// ─────────────────────────────────────────────────────── Type ──

	@Test
	@DisplayName("String class maps to VARCHAR JDBC type")
	void type_stringClass_isVarchar() {
		Attribute<String> attr = new Attribute<>("col", false, String.class);
		assertEquals(Types.VARCHAR, attr.getType());
	}

	@Test
	@DisplayName("Integer class maps to INTEGER JDBC type")
	void type_integerClass_isInteger() {
		Attribute<Integer> attr = new Attribute<>("col", false, Integer.class);
		assertEquals(Types.INTEGER, attr.getType());
	}

	@Test
	@DisplayName("Boolean class maps to BOOLEAN JDBC type")
	void type_booleanClass_isBoolean() {
		Attribute<Boolean> attr = new Attribute<>("col", false, Boolean.class);
		assertEquals(Types.BOOLEAN, attr.getType());
	}

	@Test
	@DisplayName("Double class maps to DOUBLE JDBC type")
	void type_doubleClass_isDouble() {
		Attribute<Double> attr = new Attribute<>("col", false, Double.class);
		assertEquals(Types.DOUBLE, attr.getType());
	}

	@Test
	@DisplayName("explicit JDBC type constructor stores the given type")
	void type_explicitJdbcType_stored() {
		Attribute<String> attr = new Attribute<>("col", Types.CHAR, false, String.class);
		assertEquals(Types.CHAR, attr.getType());
	}

	// ──────────────────────────────────────── Primary key / flags ──

	@Test
	@DisplayName("primaryKey=true is stored correctly")
	void primaryKey_true_stored() {
		Attribute<String> attr = new Attribute<>("id", true, String.class);
		assertTrue(attr.isPrimaryKey());
	}

	@Test
	@DisplayName("primaryKey=false is stored correctly")
	void primaryKey_false_stored() {
		Attribute<String> attr = new Attribute<>("name", false, String.class);
		assertFalse(attr.isPrimaryKey());
	}

	@Test
	@DisplayName("unique and canBeNull flags default to false")
	void flags_defaultFalse() {
		Attribute<String> attr = new Attribute<>("col", false, String.class);
		assertFalse(attr.isUnique());
		assertFalse(attr.isCanBeNull());
	}

	@Test
	@DisplayName("unique flag can be set to true")
	void unique_setTrue_reflected() {
		Attribute<String> attr = new Attribute<>("col", false, String.class);
		attr.setUnique(true);
		assertTrue(attr.isUnique());
	}

	@Test
	@DisplayName("canBeNull flag can be set to true")
	void canBeNull_setTrue_reflected() {
		Attribute<String> attr = new Attribute<>("col", false, String.class);
		attr.setCanBeNull(true);
		assertTrue(attr.isCanBeNull());
	}

	// ───────────────────────────────────────── Default value ──

	@Test
	@DisplayName("defaultValue is null by default")
	void defaultValue_initiallyNull() {
		Attribute<String> attr = new Attribute<>("col", false, String.class);
		assertNull(attr.getDefaultValue());
	}

	@Test
	@DisplayName("defaultValue can be set")
	void defaultValue_canBeSet() {
		Attribute<String> attr = new Attribute<>("col", false, String.class);
		attr.setDefaultValue("UNKNOWN");
		assertEquals("UNKNOWN", attr.getDefaultValue());
	}

	// ───────────────────────────────────────────── Foreign key ──

	@Test
	@DisplayName("foreignKey is null by default")
	void foreignKey_initiallyNull() {
		Attribute<String> attr = new Attribute<>("col", false, String.class);
		assertNull(attr.getForeignKey());
		assertNull(attr.getAssociatedTable());
	}

	@Test
	@DisplayName("setForeignKey stores both attribute and table references")
	void foreignKey_setForeignKey_storesBoth() {
		Table<?>          parentTable = new TestTable();
		Attribute<String> parentPk    = new Attribute<>("id", true, String.class);
		Attribute<String> fk          = new Attribute<>("entity_id", false, String.class);

		fk.setForeignKey(parentPk, parentTable);

		assertSame(parentPk, fk.getForeignKey());
		assertSame(parentTable, fk.getAssociatedTable());
	}
}
