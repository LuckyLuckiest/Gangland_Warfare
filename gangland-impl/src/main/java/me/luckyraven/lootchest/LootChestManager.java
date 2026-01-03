package me.luckyraven.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.LootChestTable;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.loot.LootChestService;
import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.loot.data.LootTier;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.hologram.HologramService;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LootChestManager extends LootChestService {

	private final Gangland gangland;

	public LootChestManager(Gangland gangland, HologramService hologramService) {
		super(gangland, hologramService, gangland.getFullPrefix());

		this.gangland = gangland;
	}

	public void initialize(LootChestTable table, boolean reload) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		if (reload) registerLootChests(table, helper);
		else {
			// in seconds
			int timeToWait = 5;
			CountdownTimer waitForWorld = new CountdownTimer(gangland, timeToWait, null, null, timer -> {
				registerLootChests(table, helper);
			});

			// waits for the world to load to spawn the holograms
			waitForWorld.start(false);
		}

		getSessionStartHandler().addHandler((lootChestSession -> {
			Player player = lootChestSession.getPlayer();

			var soundConfig = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
													 SettingAddon.getLootChestOpeningSound(), 1.0f, 1.0f);

			soundConfig.playSound(player);
		}));
	}

	private void registerLootChests(LootChestTable table, DatabaseHelper helper) {
		helper.runQueries(database -> {
			List<Object[]> data = database.table(table.getName()).selectAll();

			for (Object[] row : data) {
				LootChestData chestData = createFromDatabaseRow(row);
				registerChest(chestData);
			}
		});
	}

	private LootChestData createFromDatabaseRow(Object[] row) {
		UUID    id            = UUID.fromString(String.valueOf(row[0]));
		String  worldName     = String.valueOf(row[1]);
		double  x             = ((Number) row[2]).doubleValue();
		double  y             = ((Number) row[3]).doubleValue();
		double  z             = ((Number) row[4]).doubleValue();
		String  lootTableId   = String.valueOf(row[5]);
		String  tierId        = row[6] != null ? String.valueOf(row[6]) : null;
		long    respawnTime   = ((Number) row[7]).longValue();
		int     inventorySize = ((Number) row[8]).intValue();
		String  displayName   = String.valueOf(row[9]);
		long    lastOpened    = ((Number) row[10]).longValue();
		boolean isLooted      = row[11] instanceof Boolean ? (Boolean) row[11] : ((Number) row[11]).intValue() == 1;

		World    world    = Bukkit.getWorld(worldName);
		Location location = new Location(world, x, y, z);

		LootTier tier = null;
		if (tierId != null) {
			Optional<LootTier> optional = getTier(tierId);

			if (optional.isPresent()) tier = optional.get();
		}

		return LootChestData.builder()
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
	}
}
