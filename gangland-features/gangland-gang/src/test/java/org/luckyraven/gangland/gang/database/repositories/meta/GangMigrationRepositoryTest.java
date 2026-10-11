package org.luckyraven.gangland.gang.database.repositories.meta;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyCollection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.bukkit.plugin.Plugin;
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
import org.luckyraven.keystone.persistence.repository.IRepository;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	@Test
	@DisplayName("T-284: markDone writes on the calling thread, never through the async save queue")
	void markDone_writesSynchronously() throws SQLException {
		// an async save would be queued behind the boot's other writers; here the scheduler swallows it
		when(bukkit.scheduler().runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenReturn(null);
		JavaPlugin enabled = mock(JavaPlugin.class);
		when(enabled.isEnabled()).thenReturn(true);

		new GangMigrationRepository(enabled, mock(DatabaseHandler.class), backend).markDone("b");

		verify(backend).upsert(eq("gang_migration"), any(), any(), any());
	}

	private final List<Runnable> retries = new ArrayList<>();

	private GangMigrationRepository migrating() {
		return new GangMigrationRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend) {
			@Override
			protected void scheduleRetry(Runnable task, long delayTicks) {
				retries.add(task);
			}
		};
	}

	/** Stub writer: the n-th saveAllChecked call reports the n-th outcome; the last one repeats. */
	@SuppressWarnings("unchecked")
	private AbstractRepository<String> writerReporting(boolean... outcomes) {
		AbstractRepository<String> repo = mock(AbstractRepository.class);
		AtomicInteger calls = new AtomicInteger();
		doAnswer(call -> {
			int index = Math.min(calls.getAndIncrement(), outcomes.length - 1);
			((Consumer<Boolean>) call.getArgument(1)).accept(outcomes[index]);
			return null;
		}).when(repo).saveAllChecked(anyCollection(), any());
		return repo;
	}

	@Test
	@DisplayName("T-278 r1: a failed first write is retried; the migration is marked done once a retry commits")
	void failedWrite_isRetried_andMarkedDoneOnceCommitted() throws SQLException {
		GangMigrationRepository migrating = migrating();
		AbstractRepository<String> writer = writerReporting(false, true);

		migrating.migrate("m", writer, List.of("row"));
		assertEquals(1, retries.size(), "the failed write schedules one retry");
		verify(backend, never()).upsert(anyString(), any(), any(), any());

		retries.remove(0).run();

		verify(writer, times(2)).saveAllChecked(anyCollection(), any());
		verify(backend).upsert(eq("gang_migration"), any(), any(), any());
		assertTrue(retries.isEmpty(), "a committed retry schedules nothing more");
	}

	@Test
	@DisplayName("T-278 r1: after a failed write the migration counts as done for the rest of the session")
	void failedWrite_countsAsDoneForTheSession() {
		GangMigrationRepository migrating = migrating();

		migrating.migrate("m", writerReporting(false), List.of("row"));

		assertTrue(migrating.isDone("m"));
		assertTrue(migrating.isDone("m"), "a reload asks again and still gets done");
	}

	@Test
	@DisplayName("T-278 r1: retries are bounded; after the last attempt nothing more is scheduled")
	void failedWrite_retriesAreBounded() {
		GangMigrationRepository migrating = migrating();
		migrating.migrate("m", writerReporting(false), List.of("row"));

		int runs = 0;
		for (int i = 0; i < 100 && !retries.isEmpty(); i++) {
			retries.remove(0).run();
			runs++;
		}

		assertEquals(9, runs, "ten attempts in all: the first plus nine retries");
		assertTrue(migrating.isDone("m"), "an exhausted migration stays done for this session");
	}

	@Test
	@DisplayName("T-303: a retry writes the live rows, so a row removed after the failed first write is not resurrected")
	void retry_writesLiveRows() {
		GangMigrationRepository migrating = migrating();
		AbstractRepository<String> writer = writerReporting(false, true);
		List<String> live = new ArrayList<>(List.of("kept", "kicked"));

		migrating.migrate("m", writer, () -> new ArrayList<>(live));
		live.remove("kicked");
		retries.remove(0).run();

		@SuppressWarnings("unchecked")
		org.mockito.ArgumentCaptor<Collection<String>> rows = org.mockito.ArgumentCaptor.forClass(Collection.class);
		verify(writer, times(2)).saveAllChecked(rows.capture(), any());
		assertEquals(List.of("kept", "kicked"), List.copyOf(rows.getAllValues().get(0)));
		assertEquals(List.of("kept"), List.copyOf(rows.getAllValues().get(1)));
	}

	@Test
	@DisplayName("T-302: an attempt off the main thread is re-run on it, so rows.get() never reads a live collection off-thread")
	void attemptOffMainThread_rowsAreReadOnTheMainThread() {
		JavaPlugin enabled = mock(JavaPlugin.class);
		when(enabled.isEnabled()).thenReturn(true);
		GangMigrationRepository onEnabled = new GangMigrationRepository(enabled, mock(DatabaseHandler.class), backend);
		IRepository<String> writer = mock(IRepository.class);
		AtomicInteger reads = new AtomicInteger();
		java.util.function.Supplier<Collection<String>> rows = () -> {
			reads.incrementAndGet();
			return List.of("a");
		};

		bukkit.statics().when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(false);
		onEnabled.migrate("m", writer, rows);

		assertEquals(0, reads.get(), "rows must not be read on the async thread");
		verify(writer, never()).saveAll(anyCollection());
		org.mockito.ArgumentCaptor<Runnable> retry = org.mockito.ArgumentCaptor.forClass(Runnable.class);
		verify(bukkit.scheduler()).runTaskLater(eq(enabled), retry.capture(), org.mockito.ArgumentMatchers.anyLong());

		bukkit.statics().when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
		retry.getValue().run();

		assertEquals(1, reads.get());
		verify(writer).saveAll(anyCollection());
		assertTrue(onEnabled.isDone("m"));
	}

}
