package org.luckyraven.gangland.fights.database;

import org.bukkit.plugin.java.JavaPlugin;
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

@Repository(FightStats.class)
public class FightStatsRepository extends AbstractRepository<FightStats> {

	private final FightStatsTable table = new FightStatsTable();

	public FightStatsRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
	}

	@Override
	protected Collection<FightStats> doLoadAll() throws SQLException {
		List<FightStats> rows = new ArrayList<>();

		for (Object[] row : tableBackend().selectAll()) {
			rows.add(new FightStats(((Number) row[0]).intValue(), ((Number) row[1]).intValue(),
			                        ((Number) row[2]).intValue(), ((Number) row[3]).intValue()));
		}

		return rows;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<FightStats> getTable() {
		return table;
	}

	@Override
	protected void doDelete(FightStats data) throws SQLException {
		tableBackend().delete("gang_id = ?", data.gangId());
	}
}
