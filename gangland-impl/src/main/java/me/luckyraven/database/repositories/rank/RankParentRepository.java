package me.luckyraven.database.repositories.rank;

import me.luckyraven.data.rank.RankParent;
import me.luckyraven.database.tables.rank.RankParentTable;
import me.luckyraven.database.tables.rank.RankTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;


@Repository(RankParent.class)
public class RankParentRepository extends AbstractRepository<RankParent> {

	private final RankParentTable rankParentTable;

	public RankParentRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

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
		Database dataTable = getDatabase().table(rankParentTable.getName());

		Object[] existing = dataTable.select("id = ?", new Object[]{headId}, new int[]{Types.INTEGER},
		                                     new String[]{"*"});

		if (existing.length == 0) {
			insertData(new RankParent(headId, tailId), getDatabase());
		}
	}

	@Override
	protected Collection<RankParent> doLoadAll() throws SQLException {
		List<RankParent> rankParents = new ArrayList<>();
		List<Object[]>   data        = rankParentTable.selectAllTableQuery(getDatabase());

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
		Database table = getDatabase().table(rankParentTable.getName());
		table.delete("id", data.rankId(), Types.INTEGER);
	}
}
