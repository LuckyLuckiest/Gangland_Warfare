package me.luckyraven.database.repositories.lootchest;

import lombok.Setter;
import me.luckyraven.database.tables.lootchest.LootChestTable;
import me.luckyraven.lootchest.LootChestService;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.lootchest.data.LootTier;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.function.Consumer;

@Repository(LootChestData.class)
public class LootChestRepository extends AbstractRepository<LootChestData> {

	private final LootChestTable lootChestTable;

	@Setter
	private LootChestService lootChestService;

	public LootChestRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.lootChestTable = new LootChestTable();
	}

	@Override
	protected Collection<LootChestData> doLoadAll() throws SQLException {
		List<LootChestData> list = new ArrayList<>();
		List<Object[]>      data = getDatabase().table(lootChestTable.getName()).selectAll();

		for (Object[] result : data) {
			int v = 0;

			UUID    id            = UUID.fromString(String.valueOf(result[v++]));
			String  worldName     = String.valueOf(result[v++]);
			double  x             = (double) result[v++];
			double  y             = (double) result[v++];
			double  z             = (double) result[v++];
			String  lootTableId   = String.valueOf(result[v++]);
			Object  tierRaw       = result[v++];
			String  tierId        = tierRaw != null ? String.valueOf(tierRaw) : null;
			long    respawnTime   = ((Number) result[v++]).longValue();
			int     inventorySize = (int) result[v++];
			String  displayName   = String.valueOf(result[v++]);
			long    lastOpened    = ((Number) result[v++]).longValue();
			boolean isLooted      = result[v] instanceof Boolean ? (boolean) result[v] : (int) result[v] == 1;

			World    world    = Bukkit.getWorld(worldName);
			Location location = new Location(world, x, y, z);

			LootTier tier = null;
			if (tierId != null && lootChestService != null) {
				Optional<LootTier> optional = lootChestService.getTier(tierId);

				if (optional.isPresent()) tier = optional.get();
			}

			var lootChestData = LootChestData.builder()
											 .id(id)
											 .location(location)
											 .lootTableId(lootTableId)
											 .tier(tier)
											 .respawnTime(respawnTime)
											 .inventorySize(inventorySize)
											 .displayName(displayName)
											 .lastOpened(lastOpened)
											 .isLooted(isLooted)
											 .build();

			list.add(lootChestData);
		}

		return list;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<LootChestData> getTable() {
		return lootChestTable;
	}

	@Override
	protected void doDelete(LootChestData data) throws SQLException {
		Database table = getDatabase().table(lootChestTable.getName());
		table.delete("id", data.getId().toString(), Types.VARCHAR);
	}
}
