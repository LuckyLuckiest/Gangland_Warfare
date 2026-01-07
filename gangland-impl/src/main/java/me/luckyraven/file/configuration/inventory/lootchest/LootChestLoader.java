package me.luckyraven.file.configuration.inventory.lootchest;

import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.DataLoader;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.loot.LootChestService;
import me.luckyraven.loot.LootChestSettingProvider;
import me.luckyraven.loot.data.LootChestConfig;
import me.luckyraven.loot.data.LootTable;
import me.luckyraven.loot.data.LootTier;
import me.luckyraven.loot.item.LootItemReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Loader for loot chest configuration files. Follows the same pattern as other DataLoaders in the project. Settings are
 * loaded from SettingAddon, while loot tables and tiers are loaded from loot-chests.yml and tiers.yml.
 */
public class LootChestLoader extends DataLoader<LootChestConfig> {

	private static final Logger logger = LogManager.getLogger(LootChestLoader.class.getSimpleName());

	private final LootChestService         manager;
	private final LootChestSettingProvider settingsProvider;

	@Getter
	private LootChestConfig loadedConfig;

	public LootChestLoader(JavaPlugin plugin, LootChestService manager) {
		super(plugin);

		this.manager          = manager;
		this.settingsProvider = new LootChestSettingAddon();
	}

	@Override
	public void clear() {
		manager.clear();
		loadedConfig = null;
	}

	@Override
	protected void loadData(Consumer<LootChestConfig> consumer, FileManager fileManager) {
		FileConfiguration lootChestsConfig;
		FileConfiguration tiersConfig;

		try {
			// Load loot-chests.yml
			String lootChestsFileName = "loot-chests";
			fileManager.checkFileLoaded(lootChestsFileName);
			FileHandler lootChestsHandler = Objects.requireNonNull(fileManager.getFile(lootChestsFileName));
			lootChestsConfig = lootChestsHandler.getFileConfiguration();

			// Load tiers.yml
			String tiersFileName = "tiers";
			fileManager.checkFileLoaded(tiersFileName);
			FileHandler tiersHandler = Objects.requireNonNull(fileManager.getFile(tiersFileName));
			tiersConfig = tiersHandler.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		// Load tiers from tiers.yml
		Map<String, LootTier> tiers = loadTiers(tiersConfig);

		// Load global rarity settings from tiers.yml
		Map<LootItemReference.Rarity, Double> globalRarityChances = loadGlobalRaritySettings(tiersConfig);

		// Load loot tables from loot-chests.yml
		Map<String, LootTable> lootTables = loadLootTables(lootChestsConfig, globalRarityChances);

		// Build config using settings from SettingAddon via the provider
		loadedConfig = LootChestConfig.fromProvider(settingsProvider, tiers, lootTables, globalRarityChances);

		manager.setConfig(loadedConfig);

		logger.info("Loaded {} tiers and {} loot tables", tiers.size(), lootTables.size());

		if (consumer != null) {
			consumer.accept(loadedConfig);
		}
	}

	private Map<LootItemReference.Rarity, Double> loadGlobalRaritySettings(FileConfiguration config) {
		Map<LootItemReference.Rarity, Double> rarityChances = new EnumMap<>(LootItemReference.Rarity.class);

		ConfigurationSection raritySection = config.getConfigurationSection("Rarity");
		if (raritySection != null) {
			for (LootItemReference.Rarity rarity : LootItemReference.Rarity.values()) {
				String key = rarity.name().toLowerCase();
				if (raritySection.contains(key)) {
					rarityChances.put(rarity, raritySection.getDouble(key));
				}
			}
		}

		return rarityChances;
	}

	private Map<String, LootTier> loadTiers(FileConfiguration config) {
		Map<String, LootTier> tiers        = new LinkedHashMap<>();
		ConfigurationSection  tiersSection = config.getConfigurationSection("Tiers");

		if (tiersSection == null) {
			LootTier defaultTier = new LootTier("default", "&7Common", 1, LootTier.UnlockRequirement.NONE);
			tiers.put("default", defaultTier);
			return tiers;
		}

		int level = 1;
		for (String tierId : tiersSection.getKeys(false)) {
			ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierId);
			if (tierSection == null) continue;

			String displayName    = tierSection.getString("Display_Name", tierId);
			int    tierLevel      = tierSection.getInt("Level", level++);
			String requirementStr = tierSection.getString("Unlock_Requirement", "NONE");
			String unlockItemId   = tierSection.getString("Unlock_Item");

			LootTier.UnlockRequirement requirement;
			try {
				requirement = LootTier.UnlockRequirement.valueOf(requirementStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				requirement = LootTier.UnlockRequirement.NONE;
			}

			LootTier tier = new LootTier(tierId, displayName, tierLevel, requirement, unlockItemId);
			tiers.put(tierId, tier);
		}

		return tiers;
	}

	private Map<String, LootTable> loadLootTables(FileConfiguration config,
												  Map<LootItemReference.Rarity, Double> globalRarities) {
		Map<String, LootTable> lootTables    = new HashMap<>();
		ConfigurationSection   tablesSection = config.getConfigurationSection("Loot_Tables");

		if (tablesSection == null) return lootTables;

		for (String tableId : tablesSection.getKeys(false)) {
			ConfigurationSection tableSection = tablesSection.getConfigurationSection(tableId);
			if (tableSection == null) continue;

			String       displayName  = tableSection.getString("Display_Name", tableId);
			int          minItems     = tableSection.getInt("Min_Items", 1);
			int          maxItems     = tableSection.getInt("Max_Items", 5);
			List<String> allowedTiers = tableSection.getStringList("Allowed_Tiers");

			// Load rarity overrides for this table
			Map<LootItemReference.Rarity, Double> rarityOverrides = loadTableRarityOverrides(tableSection,
																							 globalRarities);

			List<LootItemReference> items = loadLootItemReferences(tableSection.getConfigurationSection("Items"));

			LootTable lootTable = new LootTable(tableId, displayName, items, minItems, maxItems, allowedTiers,
												rarityOverrides);
			lootTables.put(tableId, lootTable);
		}

		return lootTables;
	}

	private Map<LootItemReference.Rarity, Double> loadTableRarityOverrides(ConfigurationSection tableSection,
																		   Map<LootItemReference.Rarity, Double> globalRarities) {

		Map<LootItemReference.Rarity, Double> overrides = new EnumMap<>(LootItemReference.Rarity.class);
		overrides.putAll(globalRarities);

		ConfigurationSection raritySection = tableSection.getConfigurationSection("Rarity_Overrides");
		if (raritySection != null) {
			for (LootItemReference.Rarity rarity : LootItemReference.Rarity.values()) {
				String key = rarity.name().toLowerCase();
				if (raritySection.contains(key)) {
					overrides.put(rarity, raritySection.getDouble(key));
				}
			}
		}

		return overrides;
	}

	private List<LootItemReference> loadLootItemReferences(ConfigurationSection itemsSection) {
		List<LootItemReference> items = new ArrayList<>();

		if (itemsSection == null) return items;

		for (String itemId : itemsSection.getKeys(false)) {
			ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
			if (itemSection == null) continue;

			// Reference ID - the key in the original config file (e.g., "ak47", "9mm")
			String referenceId = itemSection.getString("Reference", itemId);

			// Category determines which provider method to use
			String categoryStr = itemSection.getString("Category", "MISC");

			LootItemReference.LootCategory category;
			try {
				category = LootItemReference.LootCategory.valueOf(categoryStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				category = LootItemReference.LootCategory.MISC;
			}

			// Rarity affects spawn chance
			String                   rarityStr = itemSection.getString("Rarity", "COMMON");
			LootItemReference.Rarity rarity;
			try {
				rarity = LootItemReference.Rarity.valueOf(rarityStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				rarity = LootItemReference.Rarity.COMMON;
			}

			int    minAmount       = itemSection.getInt("Min_Amount", 1);
			int    maxAmount       = itemSection.getInt("Max_Amount", 1);
			double weight          = itemSection.getDouble("Weight", 1.0);
			String tierRequirement = itemSection.getString("Tier_Requirement");

			LootItemReference lootItem = LootItemReference.builder()
														  .id(itemId)
														  .referenceId(referenceId)
														  .category(category)
														  .rarity(rarity)
														  .minAmount(minAmount)
														  .maxAmount(maxAmount)
														  .weight(weight)
														  .tierRequirement(tierRequirement)
														  .build();

			items.add(lootItem);
		}

		return items;
	}

}
