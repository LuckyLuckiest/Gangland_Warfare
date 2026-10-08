package org.luckyraven.gangland.turf.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Turf.class)
public class TurfRepository extends AbstractRepository<Turf> implements TurfRepositoryContract {

	private final TurfTable turfTable;

	public TurfRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.turfTable = new TurfTable();
	}

	@Override
	protected Collection<Turf> doLoadAll() throws SQLException {
		List<Turf>     turfs    = new ArrayList<>();
		List<Object[]> turfData = tableBackend().selectAll();

		for (Object[] result : turfData) {
			turfs.add(fromRow(result));
		}

		return turfs;
	}

	/**
	 * TF-14: maps one {@code turf} row. Numeric columns are read through {@link Number} because SQLite and MySQL hand
	 * back different Java types for the same column (Integer/Long/Float/Double), and a hard cast failed on MySQL.
	 * Package-private so TurfRepositoryTest can pin it.
	 */
	static Turf fromRow(Object[] row) {
		int    v                    = 0;
		int    id                   = ((Number) row[v++]).intValue();
		String displayName          = String.valueOf(row[v++]);
		String world                = String.valueOf(row[v++]);
		int    minX                 = ((Number) row[v++]).intValue();
		int    maxX                 = ((Number) row[v++]).intValue();
		int    minZ                 = ((Number) row[v++]).intValue();
		int    maxZ                 = ((Number) row[v++]).intValue();
		Object ownerGangId          = row[v++];
		double incomeAmount         = ((Number) row[v++]).doubleValue();
		long   createdAt            = ((Number) row[v++]).longValue();
		long   lastCaptureTimestamp = ((Number) row[v]).longValue();

		CuboidRegion region = new CuboidRegion(world, minX, minZ, maxX, maxZ);

		return new Turf(
				id,
				displayName,
				region,
				ownerGangId == null ? null : ((Number) ownerGangId).intValue(),
				sanitizeIncomeAmount(incomeAmount),
				createdAt,
				lastCaptureTimestamp);
	}

	/**
	 * GI-32: guards a persisted {@code income_amount} that somehow reached the database as {@code Infinity}/{@code
	 * NaN} (pre-fix input, hand-edited row). {@code BigDecimal.valueOf} throws {@link NumberFormatException} for
	 * either, which — unguarded — disabled the plugin on every future boot until the row was manually repaired.
	 * Package-private (not private) so TurfRepositoryTest can pin it directly.
	 */
	static BigDecimal sanitizeIncomeAmount(double raw) {
		return Double.isFinite(raw) ? BigDecimal.valueOf(raw) : Currency.ZERO;
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
		tableBackend().delete("id = ?", data.getId());
	}
}
