package org.luckyraven.gangland.gang.database.repositories.safehouse;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.database.repositories.gang.GangRepository;
import org.luckyraven.gangland.gang.database.tables.gang.GangTable;
import org.luckyraven.gangland.gang.database.tables.safehouse.GangSafehouseTable;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
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

/** Persists {@code gang_safehouse}. The service holds the rows in memory and writes through autosave. */
@Repository(Safehouse.class)
public class GangSafehouseRepository extends AbstractRepository<Safehouse> {

	private final GangSafehouseTable table;

	public GangSafehouseRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.table = new GangSafehouseTable(new GangTable());
	}

	@Override
	protected Collection<Safehouse> doLoadAll() throws SQLException {
		List<Safehouse> safehouses = new ArrayList<>();

		for (Object[] row : tableBackend().selectAll()) {
			safehouses.add(fromRow(row));
		}
		return safehouses;
	}

	/** One {@code gang_safehouse} row; numbers read through {@link Number} so a SQL NULL reads as 0 (T-199). */
	public static Safehouse fromRow(Object[] row) {
		return new Safehouse(GangRepository.num(row[0]).intValue(), String.valueOf(row[1]), String.valueOf(row[2]),
		                     GangRepository.num(row[3]).doubleValue(), GangRepository.num(row[4]).doubleValue(),
		                     GangRepository.num(row[5]).doubleValue(), GangRepository.num(row[6]).floatValue(),
		                     GangRepository.num(row[7]).floatValue());
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Safehouse> getTable() {
		return table;
	}

	@Override
	protected void doDelete(Safehouse data) throws SQLException {
		tableBackend().delete("gang_id = ? AND name = ?", data.gangId(), data.name());
	}
}
