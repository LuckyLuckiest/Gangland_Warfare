package me.luckyraven.database.repositories.market;

import me.luckyraven.database.tables.market.MarketSnapshotTable;
import me.luckyraven.market.contract.MarketSnapshotRepositoryContract;
import me.luckyraven.market.snapshot.DailySnapshot;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@Repository(DailySnapshot.class)
public class MarketSnapshotRepository extends AbstractRepository<DailySnapshot> implements
		MarketSnapshotRepositoryContract {

	private final MarketSnapshotTable table;

	public MarketSnapshotRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);
		this.table = new MarketSnapshotTable();
	}

	@Override
	public void save(DailySnapshot snapshot) {
		super.save(snapshot);
	}

	@Override
	public List<DailySnapshot> history(String itemId, int days) {
		List<DailySnapshot> matches = new ArrayList<>();
		for (DailySnapshot snapshot : safeLoad()) {
			if (!snapshot.itemId().equals(itemId)) {
				continue;
			}
			matches.add(snapshot);
		}
		matches.sort(Comparator.comparing(DailySnapshot::snapshotDate).reversed());
		return matches.size() > days ? new ArrayList<>(matches.subList(0, days)) : matches;
	}

	@Override
	public void pruneOlderThan(LocalDate cutoff) {
		// Bulk delete via raw SQL — single round trip beats iterating rows one at a time.
		String sql = "DELETE FROM " + table.getName() + " WHERE snapshot_date < '" + cutoff + "'";
		try {
			getDatabase().executeUpdate(sql);
		} catch (SQLException ignored) {
			// Fall back to per-row deletion
			for (DailySnapshot snapshot : safeLoad()) {
				if (snapshot.snapshotDate().isBefore(cutoff)) {
					delete(snapshot);
				}
			}
		}
	}

	@Override
	protected Collection<DailySnapshot> doLoadAll() throws SQLException {
		List<DailySnapshot> snapshots = new ArrayList<>();
		List<Object[]>      rows      = table.selectAllTableQuery(getDatabase());

		for (Object[] row : rows) {
			int v = 0;
			v++; // skip snapshot_id (synthetic PK)
			String    itemId       = String.valueOf(row[v++]);
			LocalDate snapshotDate = LocalDate.parse(String.valueOf(row[v++]));
			double    open         = ((Number) row[v++]).doubleValue();
			double    high         = ((Number) row[v++]).doubleValue();
			double    low          = ((Number) row[v++]).doubleValue();
			double    close        = ((Number) row[v++]).doubleValue();
			long      volume       = ((Number) row[v]).longValue();

			snapshots.add(new DailySnapshot(itemId, snapshotDate, open, high, low, close, volume));
		}
		return snapshots;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<DailySnapshot> getTable() {
		return table;
	}

	@Override
	protected void doDelete(DailySnapshot data) throws SQLException {
		Database db = getDatabase().table(table.getName());
		db.delete("snapshot_id", MarketSnapshotTable.makeId(data.itemId(), data.snapshotDate().toString()),
		          Types.VARCHAR);
	}

	private Collection<DailySnapshot> safeLoad() {
		try {
			return doLoadAll();
		} catch (SQLException e) {
			return List.of();
		}
	}
}
