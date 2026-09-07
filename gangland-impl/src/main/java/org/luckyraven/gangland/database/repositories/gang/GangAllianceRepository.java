package org.luckyraven.gangland.database.repositories.gang;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.gang.GangAllianceTable;
import org.luckyraven.gangland.database.tables.gang.GangTable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.DatabaseHelper;
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
@Repository(GangAlliance.class)
public class GangAllianceRepository extends AbstractRepository<GangAlliance> implements GangAllianceRepositoryContract {

	private final GangAllianceTable gangAllianceTable;

	private GangLookupContract gangLookup;

	public GangAllianceRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		GangTable gangTable = new GangTable();
		this.gangAllianceTable = new GangAllianceTable(gangTable);
	}

	@Override
	public void setGangLookup(GangLookupContract gangLookup) {
		this.gangLookup = gangLookup;
	}

	/**
	 * Deletes every alliance row that involves the given gang, whether it appears as the initiating gang
	 * ({@code gang_id}) or the allied gang ({@code ally_id}).
	 */
	@Override
	public void deleteAllForGang(Gang gang) {
		DatabaseHelper helper = new DatabaseHelper(getPlugin(), getDatabaseHandler());
		helper.runQueriesAsync(database -> {
			tableBackend().delete("gang_id = ?", gang.getId());
			tableBackend().delete("ally_id = ?", gang.getId());
		});
	}

	/**
	 * Flips legacy {@code gang_ally} rows (sole PK on {@code gang_id}, UNIQUE on {@code ally_id}) to a composite PK on
	 * {@code (gang_id, ally_id)} — see GR-05. Existing rows are preserved; the old schema could not hold duplicates of
	 * the new key, so no de-duplication is needed. Idempotent: on databases that already carry the composite PK,
	 * {@link SchemaMigrations#isColumnInPrimaryKey} returns true and we return early.
	 */
	@Override
	public void migrateSchema() throws SQLException {
		Connection conn   = getDatabase() == null ? null : getDatabase().getConnection();
		int        dbType = getDatabaseHandler().getType();

		if (conn == null) return;
		if (SchemaMigrations.isColumnInPrimaryKey(conn, dbType, gangAllianceTable.getName(), "ally_id")) return;

		log.warn("Detected legacy {} schema. Migrating to composite primary key...", gangAllianceTable.getName());

		switch (dbType) {
			case DatabaseHandler.SQLITE -> SchemaMigrations.rebuildSqliteTable(conn, gangAllianceTable.getName(),
			                                                                   "CREATE TABLE " +
			                                                                   gangAllianceTable.getName() +
			                                                                   "_migration (" +
			                                                                   "gang_id INTEGER NOT NULL, " +
			                                                                   "ally_id INTEGER NOT NULL, " +
			                                                                   "since INTEGER DEFAULT -1, " +
			                                                                   "PRIMARY KEY (gang_id, ally_id), " +
			                                                                   "FOREIGN KEY (gang_id) REFERENCES gang(id), " +
			                                                                   "FOREIGN KEY (ally_id) REFERENCES gang(id))",
			                                                                   "gang_id", "ally_id", "since");
			case DatabaseHandler.MYSQL -> migrateMysql(conn);
		}

		log.info("{} migration complete.", gangAllianceTable.getName());
	}

	@Override
	protected Collection<GangAlliance> doLoadAll() throws SQLException {
		List<GangAlliance> alliances      = new ArrayList<>();
		List<Object[]>     gangAlliesData = tableBackend().selectAll();

		// Load all gang alliances
		for (Object[] result : gangAlliesData) {
			int  gangId  = (int) result[0];
			int  allieId = (int) result[1];
			long since   = (long) result[2];

			Gang gang = gangLookup.findById(gangId);
			Gang ally = gangLookup.findById(allieId);

			// Orphan: one or both ends reference a gang that no longer exists. Drop the row.
			if (gang == null || ally == null) {
				log.warn("Orphan alliance row gang_id={} ally_id={}; deleting.", gangId, allieId);
				tableBackend().delete("gang_id = ? AND ally_id = ?", gangId, allieId);
				continue;
			}

			alliances.add(new GangAlliance(gang, ally, since));
		}

		return alliances;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<GangAlliance> getTable() {
		return gangAllianceTable;
	}

	@Override
	protected void doDelete(GangAlliance data) throws SQLException {
		tableBackend().delete("gang_id = ? AND ally_id = ?", data.gang().getId(), data.ally().getId());
	}

	/**
	 * MySQL leg of {@link #migrateSchema()}. The FK on {@code ally_id} is backed by the legacy UNIQUE index, so a
	 * plain index has to replace it before the UNIQUE can be dropped (MySQL error 1553 otherwise). {@code gang_id}
	 * stays leftmost in the new key, so its own FK keeps a backing index throughout.
	 */
	private void migrateMysql(Connection conn) throws SQLException {
		String table = gangAllianceTable.getName();

		try (Statement stmt = conn.createStatement()) {
			if (SchemaMigrations.hasUniqueIndex(conn, DatabaseHandler.MYSQL, table, "ally_id")) {
				stmt.execute("ALTER TABLE " + table + " ADD INDEX ally_id_idx (ally_id)");
				stmt.execute("ALTER TABLE " + table + " DROP INDEX ally_id");
			}
			stmt.execute("ALTER TABLE " + table + " DROP PRIMARY KEY, ADD PRIMARY KEY (gang_id, ally_id)");
		}
	}
}
