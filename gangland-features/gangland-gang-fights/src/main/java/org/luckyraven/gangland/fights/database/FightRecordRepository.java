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

@Repository(FightRecord.class)
public class FightRecordRepository extends AbstractRepository<FightRecord> {

	private final FightRecordTable table = new FightRecordTable();

	public FightRecordRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
	}

	@Override
	protected Collection<FightRecord> doLoadAll() throws SQLException {
		List<FightRecord> rows = new ArrayList<>();

		for (Object[] row : tableBackend().selectAll()) {
			rows.add(new FightRecord(((Number) row[0]).longValue(), ((Number) row[1]).intValue(),
			                         ((Number) row[2]).intValue(), ((Number) row[3]).intValue(),
			                         ((Number) row[4]).doubleValue(), ((Number) row[5]).intValue() != 0,
			                         ((Number) row[6]).longValue()));
		}

		return rows;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<FightRecord> getTable() {
		return table;
	}

	@Override
	protected void doDelete(FightRecord data) throws SQLException {
		tableBackend().delete("id = ?", data.id());
	}
}
