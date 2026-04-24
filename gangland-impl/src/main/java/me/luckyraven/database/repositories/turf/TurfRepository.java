package me.luckyraven.database.repositories.turf;

import me.luckyraven.database.tables.turf.TurfTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import me.luckyraven.turf.contract.TurfRepositoryContract;
import me.luckyraven.turf.data.CuboidRegion;
import me.luckyraven.turf.data.Turf;
import org.bukkit.plugin.java.JavaPlugin;

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
