package org.luckyraven.gangland.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic pin for {@link TableLookup#find(Class, List)}: the hit path (first assignable match) and the
 * {@link IllegalStateException} miss path.
 */
@DisplayName("TableLookup")
class TableLookupTest {

	private static final class AlphaTable extends Table<Object> {
		AlphaTable() {
			super("alpha");
		}

		@Override
		public Object[] getData(Object data) {
			return new Object[0];
		}

		@Override
		public Map<String, Object> searchCriteria(Object data) {
			return Map.of();
		}
	}

	private static final class BetaTable extends Table<Object> {
		BetaTable() {
			super("beta");
		}

		@Override
		public Object[] getData(Object data) {
			return new Object[0];
		}

		@Override
		public Map<String, Object> searchCriteria(Object data) {
			return Map.of();
		}
	}

	@Test
	@DisplayName("find returns the first table assignable to the requested type")
	void find_returnsFirstAssignableMatch() {
		List<Table<?>> tables = List.of(new AlphaTable(), new BetaTable());

		BetaTable found = TableLookup.find(BetaTable.class, tables);

		assertEquals("beta", found.getName());
	}

	@Test
	@DisplayName("find throws IllegalStateException naming the missing type when no table matches")
	void find_noMatch_throwsIllegalStateException() {
		List<Table<?>> tables = List.of(new AlphaTable());

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> TableLookup.find(BetaTable.class, tables));

		assertTrue(exception.getMessage().contains(BetaTable.class.getName()));
	}

	@Test
	@DisplayName("find on an empty table list throws IllegalStateException")
	void find_emptyList_throwsIllegalStateException() {
		assertThrows(IllegalStateException.class, () -> TableLookup.find(AlphaTable.class, List.of()));
	}
}
