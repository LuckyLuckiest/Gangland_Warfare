package org.luckyraven.gangland.lootchest.database;

import lombok.CustomLog;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.lootchest.LootChestService;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootTier;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

@Repository(LootChestData.class)
@CustomLog
public class LootChestRepository extends AbstractRepository<LootChestData> {

	/** A SQL NULL reads as 0; any other value is read through Number (MySQL may return Long/BigDecimal). */
	private static Number num(Object raw) {
		return raw instanceof Number number ? number : 0;
	}

	private final LootChestTable lootChestTable;

	@Setter
	private LootChestService lootChestService;

	public LootChestRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.lootChestTable = new LootChestTable();
	}

	@Override
	protected Collection<LootChestData> doLoadAll() throws SQLException {
		List<LootChestData> list = new ArrayList<>();
		List<Object[]>      data = tableBackend().selectAll();

		for (Object[] result : data) {
			int v = 0;

			UUID id;
			try {
				id = UUID.fromString(String.valueOf(result[v++]));
			} catch (IllegalArgumentException exception) {
				// T-262: a NULL or malformed uuid skips this row instead of failing the whole load
				log.warn("Skipping a loot chest row with a bad uuid '{}'", result[0]);
				continue;
			}
			String  worldName     = String.valueOf(result[v++]);
			double  x             = num(result[v++]).doubleValue();
			double  y             = num(result[v++]).doubleValue();
			double  z             = num(result[v++]).doubleValue();
			String  lootTableId   = String.valueOf(result[v++]);
			Object  tierRaw       = result[v++];
			String  tierId        = tierRaw != null ? String.valueOf(tierRaw) : null;
			long    respawnTime   = num(result[v++]).longValue();
			int     inventorySize = num(result[v++]).intValue();
			String  displayName   = String.valueOf(result[v++]);
			long    lastOpened    = num(result[v++]).longValue();
			boolean isLooted      = result[v] instanceof Boolean ? (boolean) result[v++] : num(result[v++]).intValue() == 1;
			long    cooldownEnd   = num(result[v]).longValue();

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
			                                 .cooldownEndTime(cooldownEnd)
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
		tableBackend().delete("id = ?", data.getId().toString());
	}
}
