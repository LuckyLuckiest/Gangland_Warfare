package org.luckyraven.gangland.database.tables.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.database.schema.ColumnType;
import org.luckyraven.keystone.persistence.database.schema.SchemaColumn;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code bounty_posters} ledger grows by one entry (about 57 characters) per distinct poster and nothing caps the
 * posters. As a 4096-character VARCHAR, about 70 posters overflowed it, and on a strict-mode MySQL one over-long row
 * rolled back the whole user autosave batch. It is a TEXT column (64 KB on MySQL) instead.
 */
@DisplayName("UserTable - the bounty_posters column")
class UserTableTest {

	@Test
	@DisplayName("bounty_posters is a nullable TEXT column, so a long poster ledger fits on MySQL")
	void bountyPosters_isANullableTextColumn() {
		SchemaColumn posters = TableSchemas.fromTable(new UserTable())
		                                   .columns()
		                                   .stream()
		                                   .filter(column -> column.name().equals("bounty_posters"))
		                                   .findFirst()
		                                   .orElseThrow();

		assertEquals(ColumnType.TEXT, posters.type());
		assertTrue(posters.nullable(), "NULL marks a row saved before the ledger existed");
	}

}
