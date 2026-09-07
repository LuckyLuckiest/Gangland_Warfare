package org.luckyraven.gangland.database.repositories.rank;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.rank.RankParentTable;
import org.luckyraven.gangland.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.rank.RankParent;
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
		tableBackend().delete("id = ?", data.rankId());
	}
}
