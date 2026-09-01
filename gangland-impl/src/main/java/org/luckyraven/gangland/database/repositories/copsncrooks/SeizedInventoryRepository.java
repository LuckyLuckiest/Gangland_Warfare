package org.luckyraven.gangland.database.repositories.copsncrooks;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventory;
import org.luckyraven.gangland.database.tables.copsncrooks.SeizedInventoryTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Repository(SeizedInventory.class)
public class SeizedInventoryRepository extends AbstractRepository<SeizedInventory> {

	private final SeizedInventoryTable seizedInventoryTable;

	public SeizedInventoryRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.seizedInventoryTable = new SeizedInventoryTable();
	}

	@Override
	protected Collection<SeizedInventory> doLoadAll() throws SQLException {
		List<SeizedInventory> list = new ArrayList<>();
		List<Object[]>        data = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			UUID   uuid       = UUID.fromString(String.valueOf(result[v++]));
			String serialized = String.valueOf(result[v++]);
			long   seizedAt   = ((Number) result[v]).longValue();

			list.add(new SeizedInventory(uuid, serialized, seizedAt));
		}

		return list;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<SeizedInventory> getTable() {
		return seizedInventoryTable;
	}

	@Override
	protected void doDelete(SeizedInventory data) throws SQLException {
		tableBackend().delete("player_uuid = ?", data.getPlayerId().toString());
	}
}
