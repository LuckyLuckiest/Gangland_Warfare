package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.station.Station;
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

/** Loads every station row whatever its world: a station of an unloaded world keeps its id. */
@Repository(Station.class)
public class StationRepository extends AbstractRepository<Station> {

	private final StationTable stationTable;

	public StationRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.stationTable = new StationTable();
	}

	@Override
	protected Collection<Station> doLoadAll() throws SQLException {
		List<Station>  stations = new ArrayList<>();
		List<Object[]> data     = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			int     id      = ((Number) result[v++]).intValue();
			String  name    = String.valueOf(result[v++]);
			String  world   = String.valueOf(result[v++]);
			double  x       = ((Number) result[v++]).doubleValue();
			double  y       = ((Number) result[v++]).doubleValue();
			double  z       = ((Number) result[v++]).doubleValue();
			float   yaw     = ((Number) result[v++]).floatValue();
			Integer jailId  = result[v] == null ? null : ((Number) result[v]).intValue();

			stations.add(new Station(id, name, world, x, y, z, yaw, jailId));
		}

		return stations;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Station> getTable() {
		return stationTable;
	}

	@Override
	protected void doDelete(Station data) throws SQLException {
		tableBackend().delete("id = ?", data.getId());
	}
}
