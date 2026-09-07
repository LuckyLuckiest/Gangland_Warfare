package org.luckyraven.gangland.database.repositories.copsncrooks;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.database.tables.copsncrooks.DetainmentTable;
import org.luckyraven.gangland.database.tables.copsncrooks.JailTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.SchemaMigrations;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@CustomLog
@Repository(DetainedPlayer.class)
public class DetainmentRepository extends AbstractRepository<DetainedPlayer> {

	/** MySQL {@code ER_DUP_KEYNAME}: the replacement index already exists from a half-finished earlier migration. */
	private static final int DUPLICATE_KEY_NAME = 1061;

	private final DetainmentTable detainmentTable;

	public DetainmentRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		// DetainmentTable depends on JailTable
		// But JailTable will be created by JailRepository first
		// The dependency sorting in RepositoryRegistry handles this
		JailTable jailTable = new JailTable();
		this.detainmentTable = new DetainmentTable(jailTable);
	}

	/**
	 * Drops the legacy {@code UNIQUE} constraint on {@code detainment.jail_id} — see CJ-01. The column was declared
	 * unique, so the second player detained in a cell hit a unique-constraint violation and was never persisted,
	 * making {@code Jail.Max_Capacity} above 1 unusable across restarts. Uniqueness belongs to {@code player_uuid}
	 * (the primary key); a cell legitimately holds several detainment rows sharing one {@code jail_id}.
	 *
	 * <p>Keystone's {@code SchemaDiff} compares column <em>names</em> only, so removing {@code setUnique(true)} from
	 * {@link DetainmentTable} fixes fresh installs but never rewrites a deployed table — hence this migration.
	 * Idempotent: on a fresh or already-migrated database {@link SchemaMigrations#hasUniqueIndex} returns false and
	 * we return early, so it is safe to run on every startup.
	 */
	@Override
	public void migrateSchema() throws SQLException {
		Connection conn   = getDatabase() == null ? null : getDatabase().getConnection();
		int        dbType = getDatabaseHandler().getType();

		if (conn == null) return;
		if (!SchemaMigrations.hasUniqueIndex(conn, dbType, detainmentTable.getName(), "jail_id")) return;

		log.warn("Detected legacy {} schema (UNIQUE jail_id caps every cell at one inmate). Migrating...",
		         detainmentTable.getName());

		switch (dbType) {
			case DatabaseHandler.SQLITE -> SchemaMigrations.rebuildSqliteTable(conn, detainmentTable.getName(),
			                                                                   "CREATE TABLE " +
			                                                                   detainmentTable.getName() +
			                                                                   "_migration (" +
			                                                                   "player_uuid VARCHAR(36) NOT NULL, " +
			                                                                   "jail_id INTEGER, " +
			                                                                   "state VARCHAR(255) NOT NULL, " +
			                                                                   "transit_expires_at BIGINT, " +
			                                                                   "sentence_expires_at BIGINT, " +
			                                                                   "wanted_at_arrest INTEGER, " +
			                                                                   "PRIMARY KEY (player_uuid), " +
			                                                                   "FOREIGN KEY (jail_id) REFERENCES jail(id))",
			                                                                   "player_uuid", "jail_id", "state",
			                                                                   "transit_expires_at",
			                                                                   "sentence_expires_at",
			                                                                   "wanted_at_arrest");
			case DatabaseHandler.MYSQL -> migrateMysql(conn);
		}

		log.info("{} migration complete.", detainmentTable.getName());
	}

	@Override
	protected Collection<DetainedPlayer> doLoadAll() throws SQLException {
		List<DetainedPlayer> detained = new ArrayList<>();
		List<Object[]>       data     = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			UUID    uuid      = UUID.fromString(String.valueOf(result[v++]));
			Object  rawJailId = result[v++];
			Integer jailId    = rawJailId == null ? null : ((Number) rawJailId).intValue();
			Object  rawState  = v < result.length ? result[v++] : null;
			var     state     = DetainmentState.JAILED;

			// The column is consumed exactly once above; an unparseable value must not shift the cursor again or
			// every following column would be read one position to the left.
			try {
				state = DetainmentState.valueOf(String.valueOf(rawState));
			} catch (IllegalArgumentException ignored) {
			}

			Object  rawTransit  = v < result.length ? result[v++] : null;
			Object  rawSentence = v < result.length ? result[v++] : null;
			Object  rawWanted   = v < result.length ? result[v] : null;
			Long    transitAt   = rawTransit == null ? null : ((Number) rawTransit).longValue();
			Long    sentenceAt  = rawSentence == null ? null : ((Number) rawSentence).longValue();
			Integer wantedLevel = rawWanted == null ? null : ((Number) rawWanted).intValue();

			detained.add(new DetainedPlayer(uuid, jailId, state, transitAt, sentenceAt, wantedLevel));
		}

		return detained;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<DetainedPlayer> getTable() {
		return detainmentTable;
	}

	@Override
	protected void doDelete(DetainedPlayer data) throws SQLException {
		tableBackend().delete("player_uuid = ?", data.getPlayerId().toString());
	}

	/**
	 * MySQL leg of {@link #migrateSchema()}. The FK on {@code jail_id} is backed by the legacy UNIQUE index, and MySQL
	 * refuses to drop the last index backing a foreign key (error 1553), so a plain index has to take over first.
	 *
	 * <p>Unlike the {@code gang_ally} migration there is no primary-key rewrite afterwards: {@code player_uuid} was
	 * already the PK and stays that way, so dropping the index is the whole job.
	 *
	 * <p>The ADD is tolerant of {@code jail_id_idx} already existing (error 1061): a crash between the two statements
	 * would otherwise leave the index behind and make every later retry fail before reaching the DROP.
	 */
	private void migrateMysql(Connection conn) throws SQLException {
		String table = detainmentTable.getName();

		try (Statement stmt = conn.createStatement()) {
			try {
				stmt.execute("ALTER TABLE " + table + " ADD INDEX jail_id_idx (jail_id)");
			} catch (SQLException exception) {
				if (exception.getErrorCode() != DUPLICATE_KEY_NAME) throw exception;
			}

			stmt.execute("ALTER TABLE " + table + " DROP INDEX jail_id");
		}
	}
}
