package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLevelStat;
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

@Repository(ChaseLevelStat.class)
public class ChaseLevelStatRepository extends AbstractRepository<ChaseLevelStat> {

	private final ChaseLevelStatTable table;

	public ChaseLevelStatRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.table = new ChaseLevelStatTable();
	}

	@Override
	protected Collection<ChaseLevelStat> doLoadAll() throws SQLException {
		List<ChaseLevelStat> stats = new ArrayList<>();

		for (Object[] row : tableBackend().selectAll()) {
			int v = 0;

			stats.add(new ChaseLevelStat(((Number) row[v++]).intValue(), ((Number) row[v++]).doubleValue(),
			                             ((Number) row[v++]).doubleValue(), ((Number) row[v++]).doubleValue(),
			                             ((Number) row[v++]).doubleValue(), ((Number) row[v]).longValue()));
		}

		return stats;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<ChaseLevelStat> getTable() {
		return table;
	}

	@Override
	protected void doDelete(ChaseLevelStat data) throws SQLException {
		tableBackend().delete("level = ?", data.level());
	}
}
