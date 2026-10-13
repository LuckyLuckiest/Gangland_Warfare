package org.luckyraven.gangland.gang.contract;

import org.luckyraven.keystone.persistence.repository.IRepository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * One-shot data migrations of the gang module, recorded in the {@code gang_migration} table so each runs once (W2-K).
 */
public interface GangMigrationsContract {

	/** A store where every migration counts as done (tests and callers that must not migrate). */
	GangMigrationsContract NONE = new GangMigrationsContract() {

		@Override
		public boolean isDone(String id) {
			return true;
		}

		@Override
		public void markDone(String id) {
		}

	};

	/**
	 * True when migration {@code id} already ran on this database, or its rows are still being written this session.
	 *
	 * @param id the migration id, e.g. {@code GangManager.STATE_MIGRATION}
	 */
	boolean isDone(String id);

	/**
	 * Records {@code id} as done; persisted.
	 *
	 * @param id the migration id
	 */
	void markDone(String id);

	/**
	 * Records {@code id} as done and writes it synchronously, so it is on disk before the caller goes on (T-235).
	 *
	 * @param id the migration id
	 * @throws SQLException when the write failed
	 */
	default void markDoneNow(String id) throws SQLException {
		markDone(id);
	}

	/**
	 * Saves the migrated {@code rows} to {@code repository} and marks {@code id} done. The default saves and marks at
	 * once; {@link org.luckyraven.gangland.gang.database.repositories.meta.GangMigrationRepository} overrides the
	 * supplier form with a checked write that retries a failed one and keeps the migration done for this session
	 * meanwhile.
	 *
	 * @param id         the migration id
	 * @param repository the repository the rows belong to
	 * @param rows       the migrated rows
	 * @param <T>        the entity type
	 */
	default <T> void migrate(String id, IRepository<T> repository, Collection<T> rows) {
		migrate(id, repository, () -> rows);
	}

	/**
	 * As {@link #migrate(String, IRepository, Collection)}, but a retry re-reads {@code rows}, so a row removed since
	 * the first attempt (a gang disbanded, a member kicked) is not written back (T-303).
	 *
	 * @param rows the live rows, read once per attempt
	 */
	default <T> void migrate(String id, IRepository<T> repository, Supplier<Collection<T>> rows) {
		repository.saveAll(rows.get());
		markDone(id);
	}

}
