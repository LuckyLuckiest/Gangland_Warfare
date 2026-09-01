package org.luckyraven.gangland.database.repositories.rank;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.plugin.PermissionTable;
import org.luckyraven.gangland.database.tables.rank.RankPermissionTable;
import org.luckyraven.gangland.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.rank.RankPermission;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.SchemaMigrations;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@CustomLog
@Repository(RankPermission.class)
public class RankPermissionRepository extends AbstractRepository<RankPermission> {

	private final RankPermissionTable rankPermissionTable;

	public RankPermissionRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.rankPermissionTable = new RankPermissionTable(new RankTable(), new PermissionTable());
	}

	/**
	 * Deletes every rank_permission row for a given rank. Used when a rank itself is being removed.
	 */
	public void deleteAllForRank(int rankId) throws SQLException {
		tableBackend().delete("rank_id = ?", rankId);
	}

	/**
	 * Flips legacy {@code rank_permission} rows (sole PK on {@code rank_id}, UNIQUE on {@code permission_id}) to a
	 * composite PK on {@code (rank_id, permission_id)}. Existing rows are preserved. Idempotent: on repositories that
	 * already have the composite PK, {@link SchemaMigrations#isColumnInPrimaryKey} returns true and we return early.
	 */
	@Override
	public void migrateSchema() throws SQLException {
		Connection conn   = getDatabase().getConnection();
		int        dbType = getDatabaseHandler().getType();

		if (conn == null) return;
		if (SchemaMigrations.isColumnInPrimaryKey(conn, dbType, rankPermissionTable.getName(), "permission_id")) return;

		log.warn("Detected legacy {} schema. Migrating to composite primary key...", rankPermissionTable.getName());

		switch (dbType) {
			case DatabaseHandler.SQLITE -> SchemaMigrations.rebuildSqliteTable(conn, rankPermissionTable.getName(),
			                                                                   "CREATE TABLE " +
			                                                                   rankPermissionTable.getName() +
			                                                                   "_migration (" +
			                                                                   "rank_id INTEGER NOT NULL, " +
			                                                                   "permission_id INTEGER NOT NULL, " +
			                                                                   "PRIMARY KEY (rank_id, permission_id), " +
			                                                                   "FOREIGN KEY (rank_id) REFERENCES rank(id), " +
			                                                                   "FOREIGN KEY (permission_id) REFERENCES permission(id))",
			                                                                   "rank_id", "permission_id");
			case DatabaseHandler.MYSQL -> migrateMysql(conn);
		}

		log.info("{} migration complete.", rankPermissionTable.getName());
	}

	@Override
	protected Collection<RankPermission> doLoadAll() throws SQLException {
		List<RankPermission> rankPermissions = new ArrayList<>();
		List<Object[]>       data            = tableBackend().selectAll();

		for (Object[] result : data) {
			int rankId       = (int) result[0];
			int permissionId = (int) result[1];

			rankPermissions.add(new RankPermission(rankId, permissionId));
		}

		return rankPermissions;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<RankPermission> getTable() {
		return rankPermissionTable;
	}

	@Override
	protected void doDelete(RankPermission data) throws SQLException {
		tableBackend().delete("rank_id = ? AND permission_id = ?", data.rankId(), data.permissionId());
	}

	private void migrateMysql(Connection conn) throws SQLException {
		String table = rankPermissionTable.getName();

		try (Statement stmt = conn.createStatement()) {
			if (SchemaMigrations.hasUniqueIndex(conn, DatabaseHandler.MYSQL, table, "permission_id")) {
				// The FK on permission_id uses the UNIQUE index as its backing index. Dropping the UNIQUE first would
				// leave the FK unbacked and MySQL refuses (error 1553). Add a plain index as a replacement backer
				// BEFORE dropping the UNIQUE so the FK always has an index to fall back on.
				stmt.execute("ALTER TABLE " + table + " ADD INDEX permission_id_idx (permission_id)");
				stmt.execute("ALTER TABLE " + table + " DROP INDEX permission_id");
			}
			stmt.execute("ALTER TABLE " + table + " DROP PRIMARY KEY, ADD PRIMARY KEY (rank_id, permission_id)");
		}
	}
}
