package org.luckyraven.gangland.mail.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.mail.MailItem;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins GR-31: one unreadable mail row (unknown type or status, a null column) is skipped with a warning, and the rest
 * of the load still returns. Covers the static row decoder so no database is needed.
 */
@DisplayName("MailRepository - a corrupt row is skipped, the rest still load (GR-31)")
class MailRepositoryRowsTest {

	/** Row layout matches {@link MailRepository}'s column order: id, type, status, sender, senderGang, recipient, ... */
	private static Object[] row(long id, String type, String status) {
		return new Object[]{id, type, status, null, 1, UUID.randomUUID().toString(), MailItem.NO_GANG, null, 1000L, 0L,
		                    0L, 0};
	}

	@Test
	@DisplayName("a row with an unknown type is skipped and the other two still load")
	void fromRows_skipsBadTypeAndKeepsTheRest() {
		List<MailItem> loaded = MailRepository.fromRows(List.of(row(1L, "GANG_INVITE", "PENDING"),
		                                                        row(2L, "NOT_A_TYPE", "PENDING"),
		                                                        row(3L, "GANG_INVITE", "PENDING")));

		assertEquals(2, loaded.size());
		assertEquals(1L, loaded.get(0).getId());
		assertEquals(3L, loaded.get(1).getId());
	}

	@Test
	@DisplayName("a row with an unknown status is skipped")
	void fromRows_skipsBadStatus() {
		List<MailItem> loaded = MailRepository.fromRows(List.of(row(1L, "GANG_INVITE", "PENDING"),
		                                                        row(2L, "GANG_INVITE", "NOPE")));

		assertEquals(1, loaded.size());
		assertEquals(1L, loaded.get(0).getId());
	}

	@Test
	@DisplayName("a row with a null id column is skipped")
	void fromRows_skipsNullColumn() {
		Object[] broken = row(0L, "GANG_INVITE", "PENDING");
		broken[0] = null;

		List<MailItem> loaded = MailRepository.fromRows(List.of(broken, row(5L, "GANG_INVITE", "PENDING")));

		assertEquals(1, loaded.size());
		assertEquals(5L, loaded.get(0).getId());
	}

}
