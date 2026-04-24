package me.luckyraven.database.repositories.turf;

import me.luckyraven.database.tables.turf.TurfGarrisonTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import me.luckyraven.turf.powerups.Garrison;
import me.luckyraven.turf.powerups.GarrisonRepositoryContract;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Garrison.class)
public class TurfGarrisonRepository extends AbstractRepository<Garrison> implements GarrisonRepositoryContract {

	private final TurfGarrisonTable table;

	public TurfGarrisonRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.table = new TurfGarrisonTable();
	}

	@Override
	protected Collection<Garrison> doLoadAll() throws SQLException {
		List<Garrison> rows = new ArrayList<>();
		for (Object[] result : table.selectAllTableQuery(getDatabase())) {
			int turfId = ((Number) result[0]).intValue();
			int count  = ((Number) result[1]).intValue();
			rows.add(new Garrison(turfId, count));
		}
		return rows;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Garrison> getTable() {
		return table;
	}

	@Override
	protected void doDelete(Garrison data) throws SQLException {
		Database db = getDatabase().table(table.getName());
		db.delete("turf_id", data.getTurfId(), Types.INTEGER);
	}
}
