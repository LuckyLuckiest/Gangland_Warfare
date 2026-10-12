package org.luckyraven.gangland.gang.database.repositories.rank;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.contract.LoadOutcome;
import org.luckyraven.gangland.gang.database.repositories.gang.GangRepository;
import org.luckyraven.gangland.gang.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Rank.class)
public class RankRepository extends AbstractRepository<Rank> implements LoadOutcome {

	private volatile boolean lastLoadFailed;

	private final RankTable rankTable;

	public RankRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.rankTable = new RankTable();
	}

	/**
	 * The next free rank id: {@code max(id) + 1}. {@code total + 1} collides with a surviving id after a delete leaves a
	 * gap (GR-21).
	 */
	public int nextRankId() throws SQLException {
		List<Integer> maxIds = getBackend().query("SELECT COALESCE(MAX(id), 0) AS max_id FROM " + rankTable.getName(),
		                                          resultSet -> resultSet.getInt("max_id"));

		return maxIds.isEmpty() ? 1 : maxIds.get(0) + 1;
	}

	public int totalRanks() throws SQLException {
		List<Integer> counts = getBackend().query("SELECT COUNT(*) AS total FROM " + rankTable.getName(),
		                                          resultSet -> resultSet.getInt("total"));

		return counts.isEmpty() ? 0 : counts.get(0);
	}

	/**
	 * Writes one rank synchronously. {@link AbstractRepository#save} is asynchronous, so a read right after it can miss
	 * the row (fresh-install seeding, R1/G1).
	 */
	public void insertNow(Rank rank) throws SQLException {
		tableBackend().upsert(rank);
	}

	@Override
	protected Collection<Rank> doLoadAll() throws SQLException {
		lastLoadFailed = true;   // stays true when selectAll throws; Keystone reports it and returns an empty list (T-283)
		List<Rank>     ranks = new ArrayList<>();
		List<Object[]> data  = tableBackend().selectAll();

		for (Object[] result : data) {
			ranks.add(fromRow(result));
		}

		lastLoadFailed = false;
		return ranks;
	}

	@Override
	public boolean lastLoadFailed() {
		return lastLoadFailed;
	}

	/** One rank row; the id reads through {@link Number}, a SQL NULL as 0 (T-199). */
	public static Rank fromRow(Object[] result) {
		int    id         = GangRepository.num(result[0]).intValue();
		String name       = String.valueOf(result[1]);
		String vaultGroup = result.length > 2 && result[2] != null ? String.valueOf(result[2]) : "";
		if (vaultGroup.isEmpty()) vaultGroup = null; // '' is how RankTable stores an unlinked rank

		Rank rank = new Rank(name, id);
		rank.setVaultGroup(vaultGroup);
		return rank;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Rank> getTable() {
		return rankTable;
	}

	@Override
	protected void doDelete(Rank data) throws SQLException {
		tableBackend().delete("id = ?", data.getUsedId());
	}
}
