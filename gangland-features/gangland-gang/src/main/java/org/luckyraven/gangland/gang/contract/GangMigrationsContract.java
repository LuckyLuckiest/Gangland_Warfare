package org.luckyraven.gangland.gang.contract;

import java.sql.SQLException;

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
	 * True when migration {@code id} already ran on this database.
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

}
