package org.luckyraven.gangland.gang.contract;

import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.Collection;

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
	 * Saves the migrated {@code rows} to {@code repository} and marks {@code id} done. The default saves and marks at
	 * once; {@link org.luckyraven.gangland.gang.database.repositories.meta.GangMigrationRepository} overrides it with a
	 * checked write that retries a failed one and keeps the migration done for this session meanwhile.
	 *
	 * @param id         the migration id
	 * @param repository the repository the rows belong to
	 * @param rows       the migrated rows, live references
	 * @param <T>        the entity type
	 */
	default <T> void migrate(String id, IRepository<T> repository, Collection<T> rows) {
		repository.saveAll(rows);
		markDone(id);
	}

}
