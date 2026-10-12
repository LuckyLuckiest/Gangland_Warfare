package org.luckyraven.gangland.gang.database.repositories.rank;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.contract.LoadOutcome;
import org.luckyraven.gangland.gang.database.repositories.gang.GangRepository;
import org.luckyraven.gangland.gang.database.tables.rank.RankParentTable;
import org.luckyraven.gangland.gang.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.rank.RankParent;
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
import java.util.function.Consumer;

@CustomLog
@Repository(RankParent.class)
public class RankParentRepository extends AbstractRepository<RankParent> implements LoadOutcome {

	private volatile boolean lastLoadFailed;

	private final RankParentTable rankParentTable;

	public RankParentRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.rankParentTable = new RankParentTable(new RankTable());
	}

	/**
	 * Inserts the link placing {@code childId} directly below {@code rankId} if no entry already exists for that rank id.
	 * Used by the preset seed ({@link org.luckyraven.gangland.gang.rank.RankSeed}).
	 * <p>
	 * Stored as {@code id = rankId} (the rank above) and {@code parent_id = childId} (the rank below), see
	 * {@link RankParent}.
	 *
	 * @param rankId the id of the rank above - stored as the row's {@code id}
	 * @param childId the id of the rank below - stored as the row's {@code parent_id}
	 */
	public void insertInitialRelation(int rankId, int childId) throws SQLException {
		Integer existing = getBackend().queryBuilder(rankParentTable.getName())
		                               .where("id", rankId)
		                               .one(resultSet -> resultSet.getInt("id"));

		if (existing == null) {
			tableBackend().insert(new RankParent(rankId, childId));
		}
	}

	/**
	 * Deletes every rank_parent row naming the given rank on either side. Used when a rank itself is being removed.
	 * <p>
	 * Two delete semantics on purpose: {@link #doDelete} removes the EXACT edge (the {@code rank parent remove} path),
	 * while this removes every edge touching a rank (rank deletion, GR-22).
	 */
	public void deleteAllForRank(int rankId) throws SQLException {
		tableBackend().delete("id = ? OR parent_id = ?", rankId, rankId);
	}

	/**
	 * Flips legacy {@code rank_parent} tables (sole PK on {@code id}) to a composite primary key on
	 * {@code (id, parent_id)}, so one rank can hold several links; the old key made every save of a second link
	 * overwrite the first. Rows are preserved. Idempotent: returns early once {@code parent_id} is in the key.
	 */
	@Override
	public void migrateSchema() throws SQLException {
		Connection conn   = getDatabase().getConnection();
		int        dbType = getDatabaseHandler().getType();
		String     table  = rankParentTable.getName();

		if (conn == null) return;
		if (SchemaMigrations.isColumnInPrimaryKey(conn, dbType, table, "parent_id")) return;

		log.warn("Detected legacy {} schema. Migrating to composite primary key...", table);

		switch (dbType) {
			case DatabaseHandler.SQLITE -> SchemaMigrations.rebuildSqliteTable(conn, table, "CREATE TABLE " + table +
			                                                                                "_migration (" +
			                                                                                "id INTEGER NOT NULL, " +
			                                                                                "parent_id INTEGER NOT NULL UNIQUE, " +
			                                                                                "PRIMARY KEY (id, parent_id), " +
			                                                                                "FOREIGN KEY (parent_id) REFERENCES rank_tree(id))",
			                                                                   "id", "parent_id");
			case DatabaseHandler.MYSQL -> {
				try (Statement stmt = conn.createStatement()) {
					stmt.execute("ALTER TABLE " + table + " DROP PRIMARY KEY, ADD PRIMARY KEY (id, parent_id)");
				}
			}
		}

		log.info("{} migration complete.", table);
	}

	@Override
	protected Collection<RankParent> doLoadAll() throws SQLException {
		lastLoadFailed = true;   // stays true when selectAll throws; Keystone reports it and returns an empty list (T-283)
		List<RankParent> rankParents = new ArrayList<>();
		List<Object[]>   data        = tableBackend().selectAll();

		for (Object[] result : data) {
			rankParents.add(fromRow(result));
		}

		lastLoadFailed = false;
		return rankParents;
	}

	@Override
	public boolean lastLoadFailed() {
		return lastLoadFailed;
	}

	/** One rank-parent row; both ids read through {@link Number}, a SQL NULL as 0 (T-199). */
	public static RankParent fromRow(Object[] result) {
		return new RankParent(GangRepository.num(result[0]).intValue(), GangRepository.num(result[1]).intValue());
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<RankParent> getTable() {
		return rankParentTable;
	}

	@Override
	protected void doDelete(RankParent data) throws SQLException {
		tableBackend().delete("id = ? AND parent_id = ?", data.rankId(), data.childId());
	}
}
