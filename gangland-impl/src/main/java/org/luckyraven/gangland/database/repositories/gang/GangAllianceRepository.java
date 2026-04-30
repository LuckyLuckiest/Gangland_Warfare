package org.luckyraven.gangland.database.repositories.gang;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.gang.GangAllianceTable;
import org.luckyraven.gangland.database.tables.gang.GangTable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.DatabaseHelper;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.database.query.QueryBuilder;
import org.luckyraven.gangland.persistence.repository.AbstractRepository;
import org.luckyraven.gangland.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@CustomLog
@Repository(GangAlliance.class)
public class GangAllianceRepository extends AbstractRepository<GangAlliance> implements GangAllianceRepositoryContract {

	private final GangAllianceTable gangAllianceTable;

	private GangLookupContract gangLookup;

	public GangAllianceRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

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
			String tableName = gangAllianceTable.getName();
			QueryBuilder.on(database, tableName).delete().where("gang_id", gang.getId()).execute();
			QueryBuilder.on(database, tableName).delete().where("ally_id", gang.getId()).execute();
		});
	}

	@Override
	protected Collection<GangAlliance> doLoadAll() throws SQLException {
		List<GangAlliance> alliances      = new ArrayList<>();
		List<Object[]>     gangAlliesData = gangAllianceTable.selectAllTableQuery(getDatabase());

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
				QueryBuilder.on(getDatabase(), gangAllianceTable.getName())
				            .delete()
				            .where("gang_id", gangId)
				            .where("ally_id", allieId)
				            .execute();
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
		QueryBuilder.on(getDatabase(), gangAllianceTable.getName())
		            .delete()
		            .where("gang_id", data.gang().getId())
		            .where("ally_id", data.ally().getId())
		            .execute();
	}
}
