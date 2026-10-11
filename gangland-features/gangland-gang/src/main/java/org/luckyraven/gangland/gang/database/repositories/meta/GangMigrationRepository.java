package org.luckyraven.gangland.gang.database.repositories.meta;

import lombok.CustomLog;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

	private final GangMigrationTable table;
	private final Map<String, Long>  doneAt = new HashMap<>();
	private       boolean            loaded;

	public GangMigrationRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.table = new GangMigrationTable();
		// Nothing to write until the table was read: an unloaded repository must not erase the stored migrations.
		setDataSupplier(this::rows);
	}

	/**
	 * Saves {@code rows}, then runs {@code then} only if the write committed (T-278, Keystone 1.15.2
	 * {@code saveAllChecked}): a failed write leaves the migration not done, so the next boot runs it again. A repository
	 * without the checked callback saves and runs {@code then} at once.
	 */
	public static <T> void saveThen(IRepository<T> repository, Collection<T> rows, Runnable then) {
		if (repository instanceof AbstractRepository<T> abstractRepository) {
			abstractRepository.saveAllChecked(rows, committed -> {
				if (Boolean.TRUE.equals(committed)) {
					then.run();
				} else {
					log.warn("Migration rows could not be written; the migration is not marked done and runs again next boot");
				}
			});
			return;
		}
		repository.saveAll(rows);
		then.run();
	}

	/**
	 * T-249: an unreadable {@code gang_migration} counts as done this boot, so a migration never runs over a table it
	 * could not read; the next call reads the table again.
	 */
	@Override
	public synchronized boolean isDone(String id) {
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
