package org.luckyraven.gangland.turf.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;
import org.luckyraven.gangland.turf.powerups.Garrison;
import org.luckyraven.gangland.turf.powerups.GarrisonRepositoryContract;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Garrison.class)
public class TurfGarrisonRepository extends AbstractRepository<Garrison> implements GarrisonRepositoryContract {

	private final TurfGarrisonTable table;

	public TurfGarrisonRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.table = new TurfGarrisonTable();
	}

	@Override
	protected Collection<Garrison> doLoadAll() throws SQLException {
		List<Garrison> rows = new ArrayList<>();
		for (Object[] result : tableBackend().selectAll()) {
			int turfId = ((Number) result[0]).intValue();
			int count  = ((Number) result[1]).intValue();
			// hold was added in 0.19.0: an older or shorter row reads as Defend
			boolean hold = result.length > 2 && flag(result[2]);
			rows.add(new Garrison(turfId, count, hold));
		}
		return rows;
	}

	private static boolean flag(Object raw) {
		if (raw instanceof Boolean bool) return bool;
		if (raw instanceof Number number) return number.intValue() != 0;
		return raw != null && ("true".equalsIgnoreCase(String.valueOf(raw)) || "1".equals(String.valueOf(raw)));
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
		tableBackend().delete("turf_id = ?", data.getTurfId());
	}
}
