package org.luckyraven.gangland.database.repositories.rank;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.rank.RankTable;
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
public class RankRepository extends AbstractRepository<Rank> {

	private final RankTable rankTable;

	public RankRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.rankTable = new RankTable();
	}

	/**
	 * Inserts the initial tail and head ranks if they do not already exist in the database.
	 * <p>
	 * The tail rank is inserted first (lower rank), followed by the head rank (leader). Each rank only gets created
	 * when absent, making this method safe to call on every startup.
	 *
	 * @param headName the name of the head (leader) rank
	 * @param tailName the name of the tail (lowest) rank
	 *
	 * @return {@code int[0]} = head rank id, {@code int[1]} = tail rank id
	 */
	public int[] insertInitialRanks(String headName, String tailName) throws SQLException {
		// Tail rank first (lower rank), then the head rank — same insertion order as before.
		int tailId = resolveOrInsertRank(tailName);
		int headId = resolveOrInsertRank(headName);

		return new int[]{headId, tailId};
	}

	private int resolveOrInsertRank(String name) throws SQLException {
		Integer existingId = getBackend().queryBuilder(rankTable.getName())
		                                 .where("name", name)
		                                 .one(resultSet -> resultSet.getInt("id"));
		if (existingId != null) {
			return existingId;
		}

		int id = totalRanks() + 1;
		tableBackend().insert(new Rank(name, id));

		return id;
	}

	private int totalRanks() throws SQLException {
		List<Integer> counts = getBackend().query("SELECT COUNT(*) AS total FROM " + rankTable.getName(),
		                                          resultSet -> resultSet.getInt("total"));

		return counts.isEmpty() ? 0 : counts.get(0);
	}

	@Override
	protected Collection<Rank> doLoadAll() throws SQLException {
		List<Rank>     ranks = new ArrayList<>();
		List<Object[]> data  = tableBackend().selectAll();

		for (Object[] result : data) {
			int    id         = (int) result[0];
			String name       = String.valueOf(result[1]);
			String vaultGroup = result.length > 2 && result[2] != null ? String.valueOf(result[2]) : null;

			Rank rank = new Rank(name, id);
			rank.setVaultGroup(vaultGroup);
			ranks.add(rank);
		}

		return ranks;
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
