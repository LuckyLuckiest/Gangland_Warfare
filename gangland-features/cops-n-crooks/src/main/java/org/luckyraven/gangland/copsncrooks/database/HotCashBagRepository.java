package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.bag.BagEntry;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Saves the hot-cash bags. Its data supplier ({@code HotCashBags#all}) is wired where the store is created.
 *
 * @since 0.20.0
 */
@Repository(BagEntry.class)
public class HotCashBagRepository extends AbstractRepository<BagEntry> {

	private final HotCashBagTable table;

	public HotCashBagRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.table = new HotCashBagTable();
	}

	@Override
	protected Collection<BagEntry> doLoadAll() throws SQLException {
		List<BagEntry> bags = new ArrayList<>();

		for (Object[] row : tableBackend().selectAll()) {
			int v = 0;

			UUID   player;
			String raw = String.valueOf(row[v++]);
			try {
				player = UUID.fromString(raw);
			} catch (IllegalArgumentException e) {
				continue;
			}

			bags.add(new BagEntry(player, decimal(row[v++]), decimal(row[v++]), ((Number) row[v++]).longValue(),
			                      decimal(row[v++]), decimal(row[v])));
		}

		return bags;
	}

	private static BigDecimal decimal(Object value) {
		return BigDecimal.valueOf(((Number) value).doubleValue());
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<BagEntry> getTable() {
		return table;
	}

	@Override
	protected void doDelete(BagEntry data) throws SQLException {
		tableBackend().delete("player_uuid = ?", data.player().toString());
	}
}
