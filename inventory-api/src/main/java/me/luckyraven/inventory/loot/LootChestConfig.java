package me.luckyraven.inventory.loot;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.inventory.loot.item.LootItemReference;

import java.util.List;
import java.util.Map;

/**
 * Configuration holder for loot chest settings
 */
@Getter
@Builder
public class LootChestConfig {

	private final Map<String, LootTier>  tiers;
	private final Map<String, LootTable> lootTables;
	private final long                   defaultCountdownTime;
	private final String                 openingSound;
	private final String                 lockedSound;
	private final List<String>           allowedBlockTypes;

	// Global rarity settings
	private final Map<LootItemReference.Rarity, Double> globalRarityChances;

	/**
	 * Creates a LootChestConfig from a settings provider and loaded data.
	 */
	public static LootChestConfig fromProvider(LootChestSettingProvider settingsProvider, Map<String, LootTier> tiers,
											   Map<String, LootTable> lootTables,
											   Map<LootItemReference.Rarity, Double> globalRarityChances) {

		return LootChestConfig.builder()
							  .tiers(tiers)
							  .lootTables(lootTables)
							  .defaultCountdownTime(settingsProvider.getCountdownTimer())
							  .openingSound(settingsProvider.getOpeningSound())
							  .lockedSound(settingsProvider.getLockedSound())
							  .allowedBlockTypes(settingsProvider.getAllowedBlocks())
							  .globalRarityChances(globalRarityChances)
							  .build();
	}

}
