package org.luckyraven.gangland.gang.database.repositories.meta;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.contract.GangMigrationsContract;
import org.luckyraven.gangland.gang.database.tables.meta.GangMigrationTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Records which one-shot gang data migrations ran ({@code gang_migration}, W2-K). A migration is looked up once from
 * the table and then answered from memory; {@link #markDone} writes the row synchronously.
 *
 * <p>{@code done_at} is the moment the migration ran: it is read from the table and kept, and is only stamped by
 * {@link #markDone}. Autosave and shutdown rewrite the stored value, never re-stamp it (T-250).
 */
@CustomLog
@Repository(GangMigration.class)
public class GangMigrationRepository extends AbstractRepository<GangMigration> implements GangMigrationsContract {

	/** Thirty seconds between attempts; ten attempts in all. ponytail: fixed bound, a reboot runs the migration again. */
	private static final long RETRY_TICKS   = 20L * 30;
	private static final int  MAX_ATTEMPTS  = 10;

	private final JavaPlugin                plugin;
	private final GangMigrationTable        table;
	private final Map<String, Long>         doneAt   = new HashMap<>();
	/** Migrations whose rows did not commit yet: done for this session so a reload cannot run them again. */
	private final Set<String>               inFlight = new HashSet<>();
	private       boolean                   loaded;

	public GangMigrationRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.plugin = plugin;
		this.table = new GangMigrationTable();
		// Nothing to write until the table was read: an unloaded repository must not erase the stored migrations.
		setDataSupplier(this::rows);
	}

	/**
	 * Saves {@code rows} of migration {@code id} and marks it done once the write committed (T-278, Keystone 1.15.2
	 * {@code saveAllChecked}). A failed write is retried on a delay and re-reads {@code rows}, so a retry writes the
	 * current state (T-303). Until a write commits the migration counts as done for this session, so a reload
	 * cannot run it again; if no retry commits before the server stops, the next boot runs it again.
	 */
	@Override
	public <T> void migrate(String id, IRepository<T> repository, Supplier<Collection<T>> rows) {
		synchronized (this) {
			inFlight.add(id);
		}
		attempt(id, repository, rows, 1);
	}

	private <T> void attempt(String id, IRepository<T> repository, Supplier<Collection<T>> rows, int attempt) {
		// rows.get() must run on the main thread: a caller on an async worker would iterate live state off-thread (T-302).
		if (plugin.isEnabled() && !Bukkit.isPrimaryThread()) {
			scheduleRetry(() -> attempt(id, repository, rows, attempt), 0L);
			return;
		}
		if (!(repository instanceof AbstractRepository<T> abstractRepository)) {
			repository.saveAll(rows.get());
			markDone(id);
			return;
		}
		abstractRepository.saveAllChecked(rows.get(), committed -> {
			if (Boolean.TRUE.equals(committed)) {
				markDone(id);
			} else if (attempt < MAX_ATTEMPTS) {
				log.warn("Migration {} rows could not be written (attempt {}); retrying in 30 s", id, attempt);
				scheduleRetry(() -> attempt(id, repository, rows, attempt + 1), RETRY_TICKS);
			} else {
				log.error("Migration {} could not be written after {} attempts; it stays done for this session and runs again on the next boot",
				          id, attempt);
			}
		});
	}

	/** Runs {@code task} on the main thread after {@code delayTicks}; a seam for tests. */
	protected void scheduleRetry(Runnable task, long delayTicks) {
		Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
	}

	/**
	 * T-249: an unreadable {@code gang_migration} counts as done this boot, so a migration never runs over a table it
	 * could not read; the next call reads the table again.
	 */
	@Override
	public synchronized boolean isDone(String id) {
		if (inFlight.contains(id)) {
			return true;
		}
		if (!loaded) {
			try {
				for (GangMigration row : doLoadAll()) {
					doneAt.putIfAbsent(row.id(), row.doneAt());
				}
				loaded = true;
			} catch (SQLException exception) {
				log.warn("gang_migration could not be read ({}); migration {} is skipped for now", exception.getMessage(), id);
				return true;
			}
		}
		return doneAt.containsKey(id);
	}

	@Override
	public synchronized void markDone(String id) {
		long now = System.currentTimeMillis();
		doneAt.put(id, now);
		inFlight.remove(id);
		// T-284: written on the calling thread, not queued behind the boot's other async writers (first-tick
		// SQLITE_BUSY). On failure the in-memory row is written by the next autosave.
		try {
			tableBackend().upsert(new GangMigration(id, now));
		} catch (SQLException exception) {
			log.warn("gang_migration {} could not be written ({}); the next autosave retries", id, exception.getMessage());
		}
	}

	/** One row per migration with the done_at it was stamped with; never re-stamped (T-250). */
	synchronized List<GangMigration> rows() {
		List<GangMigration> rows = new ArrayList<>();
		for (Map.Entry<String, Long> entry : doneAt.entrySet()) {
			rows.add(new GangMigration(entry.getKey(), entry.getValue()));
		}
		return rows;
	}

	@Override
	protected Collection<GangMigration> doLoadAll() throws SQLException {
		List<GangMigration> rows = new ArrayList<>();
		for (Object[] result : tableBackend().selectAll()) {
			long doneAt = result[1] instanceof Number number ? number.longValue() : 0L;
			rows.add(new GangMigration(String.valueOf(result[0]), doneAt));
		}
		return rows;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<GangMigration> getTable() {
		return table;
	}

	@Override
	protected void doDelete(GangMigration data) throws SQLException {
		tableBackend().delete("id = ?", data.id());
	}

}
