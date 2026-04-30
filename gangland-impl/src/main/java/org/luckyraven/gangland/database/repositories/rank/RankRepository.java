package org.luckyraven.gangland.database.repositories.rank;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.persistence.database.Database;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.repository.AbstractRepository;
import org.luckyraven.gangland.persistence.repository.Repository;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Rank.class)
public class RankRepository extends AbstractRepository<Rank> {

	private final RankTable rankTable;

	public RankRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

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
		Database dataTable = getDatabase().table(rankTable.getName());

		// Resolve or insert the tail rank
		Object[] tailRow = dataTable.select("name = ?", new Object[]{tailName}, new int[]{Types.VARCHAR},
		                                    new String[]{"*"});
		int tailId;

		if (tailRow.length == 0) {
			tailId = dataTable.totalRows() + 1;
			getTable().insertTableQuery(getDatabase(), new Rank(tailName, tailId));
		} else {
			tailId = (int) tailRow[0];
		}

		// Resolve or insert the head rank
		Object[] headRow = dataTable.select("name = ?", new Object[]{headName}, new int[]{Types.VARCHAR},
		                                    new String[]{"*"});
		int headId;

		if (headRow.length == 0) {
			headId = dataTable.totalRows() + 1;
			getTable().insertTableQuery(getDatabase(), new Rank(headName, headId));
		} else {
			headId = (int) headRow[0];
		}

		return new int[]{headId, tailId};
	}

	@Override
	protected Collection<Rank> doLoadAll() throws SQLException {
		List<Rank>     ranks = new ArrayList<>();
		List<Object[]> data  = rankTable.selectAllTableQuery(getDatabase());

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
		Database table = getDatabase().table(rankTable.getName());
		table.delete("id", data.getUsedId(), Types.INTEGER);
	}
}
