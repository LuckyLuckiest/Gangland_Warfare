package org.luckyraven.gangland.database.repositories.turf;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.turf.TurfTable;
import org.luckyraven.gangland.persistence.database.Database;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.repository.AbstractRepository;
import org.luckyraven.gangland.persistence.repository.Repository;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Turf.class)
public class TurfRepository extends AbstractRepository<Turf> implements TurfRepositoryContract {

	private final TurfTable turfTable;

	public TurfRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.turfTable = new TurfTable();
	}

	@Override
	protected Collection<Turf> doLoadAll() throws SQLException {
		List<Turf>     turfs    = new ArrayList<>();
		List<Object[]> turfData = turfTable.selectAllTableQuery(getDatabase());

		for (Object[] result : turfData) {
			int    v                    = 0;
			int    id                   = ((Number) result[v++]).intValue();
			String displayName          = String.valueOf(result[v++]);
			String world                = String.valueOf(result[v++]);
			int    minX                 = (int) result[v++];
			int    maxX                 = (int) result[v++];
			int    minZ                 = (int) result[v++];
			int    maxZ                 = (int) result[v++];
			Object ownerGangId          = result[v++];
			double incomeAmount         = (double) result[v++];
			long   createdAt            = (long) result[v++];
			long   lastCaptureTimestamp = (long) result[v];

			CuboidRegion region = new CuboidRegion(world, minX, minZ, maxX, maxZ);

			Turf turf = new Turf(
					id,
					displayName,
					region,
					ownerGangId == null ? null : (Integer) ownerGangId,
					BigDecimal.valueOf(incomeAmount),
					createdAt,
					lastCaptureTimestamp);

			turfs.add(turf);
		}

		return turfs;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Turf> getTable() {
		return turfTable;
	}

	@Override
	protected void doDelete(Turf data) throws SQLException {
		Database table = getDatabase().table(turfTable.getName());
		table.delete("id", data.getId(), Types.INTEGER);
	}
}
