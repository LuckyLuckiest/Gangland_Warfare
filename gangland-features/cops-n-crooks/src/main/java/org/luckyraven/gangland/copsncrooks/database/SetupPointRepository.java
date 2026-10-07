package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.place.SetupPoint;
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

/** Loads every setup-point row whatever its world. */
@Repository(SetupPoint.class)
public class SetupPointRepository extends AbstractRepository<SetupPoint> {

	private final SetupPointTable pointTable;

	public SetupPointRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.pointTable = new SetupPointTable();
	}

	@Override
	protected Collection<SetupPoint> doLoadAll() throws SQLException {
		List<SetupPoint> points = new ArrayList<>();
		List<Object[]>   data   = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			int    id    = ((Number) result[v++]).intValue();
			String kind  = String.valueOf(result[v++]);
			String name  = String.valueOf(result[v++]);
			String world = String.valueOf(result[v++]);
			double x     = ((Number) result[v++]).doubleValue();
			double y     = ((Number) result[v++]).doubleValue();
			double z     = ((Number) result[v]).doubleValue();

			points.add(new SetupPoint(id, kind, name, world, x, y, z));
		}

		return points;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<SetupPoint> getTable() {
		return pointTable;
	}

	@Override
	protected void doDelete(SetupPoint data) throws SQLException {
		tableBackend().delete("id = ?", data.getId());
	}
}
