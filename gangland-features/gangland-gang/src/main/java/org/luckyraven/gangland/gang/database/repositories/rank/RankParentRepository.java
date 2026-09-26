package org.luckyraven.gangland.gang.database.repositories.rank;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
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
public class RankParentRepository extends AbstractRepository<RankParent> {

	private final RankParentTable rankParentTable;

	public RankParentRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.rankParentTable = new RankParentTable(new RankTable());
	}

	/**
	 * Inserts the initial head→tail parent relationship if no entry already exists for the given head rank id. Safe to
	 * call on every startup.
	 *
	 * @param headId the id of the head (leader) rank - stored as the parent row's {@code id}
	 * @param tailId the id of the tail (lowest) rank - stored as the parent row's {@code parent_id}
	 */
	public void insertInitialRelation(int headId, int tailId) throws SQLException {
		Integer existing = getBackend().queryBuilder(rankParentTable.getName())
		                               .where("id", headId)
		                               .one(resultSet -> resultSet.getInt("id"));

		if (existing == null) {
			tableBackend().insert(new RankParent(headId, tailId));
		}
	}

	/**
	 * Deletes every rank_parent row naming the given rank on either side. Used when a rank itself is being removed.
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
		List<RankParent> rankParents = new ArrayList<>();
		List<Object[]>   data        = tableBackend().selectAll();

		for (Object[] result : data) {
			int rankId   = (int) result[0];
			int parentId = (int) result[1];

			rankParents.add(new RankParent(rankId, parentId));
		}

		return rankParents;
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
		tableBackend().delete("id = ? AND parent_id = ?", data.rankId(), data.parentId());
	}
}
