package me.luckyraven.database.repositories.copsncrooks;

import me.luckyraven.copsncrooks.detainment.inventory.SeizedInventory;
import me.luckyraven.database.tables.copsncrooks.SeizedInventoryTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Repository(SeizedInventory.class)
public class SeizedInventoryRepository extends AbstractRepository<SeizedInventory> {

	private final SeizedInventoryTable seizedInventoryTable;

	public SeizedInventoryRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);
		this.seizedInventoryTable = new SeizedInventoryTable();
	}

	@Override
	protected Collection<SeizedInventory> doLoadAll() throws SQLException {
		List<SeizedInventory> list = new ArrayList<>();
		List<Object[]>        data = seizedInventoryTable.selectAllTableQuery(getDatabase());

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
		Database table = getDatabase().table(seizedInventoryTable.getName());
		table.delete("player_uuid", data.getPlayerId().toString(), Types.VARCHAR);
	}
}
