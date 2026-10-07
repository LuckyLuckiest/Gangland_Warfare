package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseHabit;
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

@Repository(ChaseHabit.class)
public class ChaseHabitRepository extends AbstractRepository<ChaseHabit> {

	private final ChaseHabitTable table;

	public ChaseHabitRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.table = new ChaseHabitTable();
	}

	@Override
	protected Collection<ChaseHabit> doLoadAll() throws SQLException {
		List<ChaseHabit> habits = new ArrayList<>();

		for (Object[] row : tableBackend().selectAll()) {
			int v = 0;

			UUID   player;
			String raw = String.valueOf(row[v++]);
			try {
				player = UUID.fromString(raw);
			} catch (IllegalArgumentException e) {
				continue;
			}

			habits.add(new ChaseHabit(player, ((Number) row[v++]).doubleValue(), ((Number) row[v++]).doubleValue(),
			                          ((Number) row[v++]).doubleValue(), ((Number) row[v]).longValue()));
		}

		return habits;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<ChaseHabit> getTable() {
		return table;
	}

	@Override
	protected void doDelete(ChaseHabit data) throws SQLException {
		tableBackend().delete("player_uuid = ?", data.player().toString());
	}
}
