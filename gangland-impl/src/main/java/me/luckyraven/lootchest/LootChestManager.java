package me.luckyraven.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.LootChestTable;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.inventory.loot.LootChestService;
import me.luckyraven.inventory.loot.data.LootChestData;
import me.luckyraven.inventory.loot.data.LootTier;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.hologram.HologramService;
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

	public void initialize(LootChestTable table) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			List<Object[]> data = database.table(table.getName()).selectAll();

			for (Object[] row : data) {
				LootChestData chestData = createFromDatabaseRow(row);
				registerChest(chestData);
			}
		});

		// Global cooldown tick - updates hologram automatically via ChestCooldownManager
		setOnChestCooldownTick((chestData, remainingSeconds) -> {
			// You can add additional effects here (particles, sounds, etc.)
		});

		// When global cooldown completes
		setOnChestCooldownComplete(chestData -> {
			// Hologram is already updated to "AVAILABLE" by ChestCooldownManager
			// Add any additional effects (sounds, particles, etc.)
		});

		// Player session countdown tick (for opening animation)
		setOnCountdownTick(session -> {
			Player player = session.getPlayer();

			// Show countdown to the player (action bar, title, etc.)
		});

		// When player finishes opening the chest
		setOnSessionComplete(session -> {
			Player player = session.getPlayer();

			// The chest inventory is now open for the player
		});

		setOnSessionStart(session -> {
			Player player = session.getPlayer();

			var soundConfig = new SoundConfiguration(SoundConfiguration.SoundType.CUSTOM,
													 SettingAddon.getLootChestOpeningSound(), 1.0f, 1.0f);

			soundConfig.playSound(player);
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
