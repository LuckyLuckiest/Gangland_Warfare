package org.luckyraven.gangland.gang.database.repositories.meta;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * gang_migration rows: a stored done_at survives autosave (T-250), and a failed read counts as done this boot so a
 * migration never runs over an unreadable table (T-249).
 */
@DisplayName("GangMigrationRepository - done_at is kept; a failed read is retried, never migrated over")
class GangMigrationRepositoryTest {

	private BukkitStatics           bukkit;
	private DatabaseBackend         backend;
	private GangMigrationRepository repository;

	@BeforeEach
	void setUp() {
		bukkit     = BukkitStatics.install();
		backend    = mock(DatabaseBackend.class);
		repository = new GangMigrationRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private void rows(Object[]... rows) throws SQLException {
		doReturn(Arrays.asList(rows)).when(backend).query(anyString(), any());
	}

	@Test
	@DisplayName("T-250: a stored done_at is kept by the data supplier, not re-stamped with now")
	void storedDoneAt_isKeptByTheDataSupplier() throws SQLException {
		rows(new Object[] { "0.17.0-gang-state-invite", 1000L });

		assertTrue(repository.isDone("0.17.0-gang-state-invite"));

		List<GangMigration> stored = repository.rows();
		assertEquals(1, stored.size());
		assertEquals(1000L, stored.get(0).doneAt());
	}

	@Test
	@DisplayName("T-249: a failed read counts as done and writes nothing")
	void failedRead_countsAsDone_andWritesNothing() throws SQLException {
		doThrow(new SQLException("busy")).when(backend).query(anyString(), any());

		assertTrue(repository.isDone("x"));
		assertTrue(repository.rows().isEmpty());
	}

	@Test
	@DisplayName("T-249: a failed read is read again on the next call")
	void failedRead_isReadAgainOnTheNextCall() throws SQLException {
		doThrow(new SQLException("busy")).when(backend).query(anyString(), any());
		repository.isDone("a");

		rows(new Object[] { "a", 5L });

		assertTrue(repository.isDone("a"));
	}

	@Test
	@DisplayName("markDone stamps only the given id; the stored row keeps its own done_at")
	void markDone_stampsOnlyThatId() throws SQLException {
		rows(new Object[] { "a", 1000L });
		repository.isDone("a");
		long before = System.currentTimeMillis();

		repository.markDone("b");

		long aStamp = -1;
		long bStamp = -1;
		for (GangMigration row : repository.rows()) {
			if (row.id().equals("a")) aStamp = row.doneAt();
			if (row.id().equals("b")) bStamp = row.doneAt();
		}
		assertEquals(1000L, aStamp);
		assertTrue(bStamp >= before, "b is stamped at or after the call");
	}

}
