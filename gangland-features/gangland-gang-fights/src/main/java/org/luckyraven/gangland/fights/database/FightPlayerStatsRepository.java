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
import java.util.UUID;
import java.util.function.Consumer;

@Repository(FightPlayerStats.class)
public class FightPlayerStatsRepository extends AbstractRepository<FightPlayerStats> {

	private final FightPlayerStatsTable table = new FightPlayerStatsTable();

	public FightPlayerStatsRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
	}

	@Override
	protected Collection<FightPlayerStats> doLoadAll() throws SQLException {
		List<FightPlayerStats> rows = new ArrayList<>();

		for (Object[] row : tableBackend().selectAll()) {
			UUID uuid;
			try {
				uuid = UUID.fromString(String.valueOf(row[0]));
			} catch (IllegalArgumentException e) {
				continue;
			}
			rows.add(new FightPlayerStats(uuid, ((Number) row[1]).intValue(), ((Number) row[2]).intValue(),
			                              ((Number) row[3]).intValue()));
		}

		return rows;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<FightPlayerStats> getTable() {
		return table;
	}

	@Override
	protected void doDelete(FightPlayerStats data) throws SQLException {
		tableBackend().delete("uuid = ?", data.uuid().toString());
	}
}
