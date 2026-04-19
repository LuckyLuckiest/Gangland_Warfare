package me.luckyraven.lootchest.config;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.lootchest.LootChestService;
import me.luckyraven.lootchest.data.LootTable;
import me.luckyraven.lootchest.data.LootTier;
import me.luckyraven.lootchest.item.LootItemReference;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileLoader;
import me.luckyraven.persistence.FileManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

@CustomLog
public class LootChestLoader extends FileLoader<LootChestConfig> {

	private final LootChestService          manager;
	private final LootChestSettingsProvider settingsProvider;

	@Getter
	private LootChestConfig loadedConfig;

	public LootChestLoader(JavaPlugin plugin, LootChestService manager, LootChestSettingsProvider settingsProvider,
	                       boolean disable, Consumer<LootChestConfig> consumer, FileManager fileManager) {
		super(plugin, disable, consumer, fileManager);

		this.manager          = manager;
		this.settingsProvider = settingsProvider;
	}

	@Override
	public void clear() {
		manager.clear();
		loadedConfig = null;
	}

	/**
	 * Returns the primary loot chest YAML (loot_chests.yml). This loader also reads tiers.yml, but the FileInitializer
	 * contract only exposes one handler; if the failure turns out to be in tiers.yml, the retry will fail again and
	 * {@link FileManager#initializeAll()} will log it loudly — which is still better than the previous silent-crash
	 * behaviour.
	 */
	@Override
	protected FileHandler resolvePrimaryHandler(FileManager fileManager) {
		return fileManager.getFile("loot_chests");
	}

	@Override
	protected void loadData(Consumer<LootChestConfig> consumer, FileManager fileManager) {
		FileConfiguration lootChestsConfig;
		FileConfiguration tiersConfig;

		try {
			// Load loot_chests.yml
			String lootChestsFileName = "loot_chests";
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

		// Load loot tables from loot_chests.yml
		Map<String, LootTable> lootTables = loadLootTables(lootChestsConfig, globalRarityChances);

		// Build config using settings from SettingAddon via the provider
		loadedConfig = LootChestConfig.fromProvider(settingsProvider, tiers, lootTables, globalRarityChances);

		manager.setConfig(loadedConfig);

		log.info("Loaded {} tiers and {} loot tables", tiers.size(), lootTables.size());

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

			String displayName       = tierSection.getString("Display_Name", tierId);
			int    tierLevel         = tierSection.getInt("Level", level++);
			String requirementStr    = tierSection.getString("Unlock_Requirement", "NONE");
			String unlockItemId      = tierSection.getString("Unlock_Item");
			String unlockItemDisplay = tierSection.getString("Unlock_Item_Display");
			String floatingItemIcon  = tierSection.getString("Floating_Item_Icon");

			LootTier.UnlockRequirement requirement;
			try {
				requirement = LootTier.UnlockRequirement.valueOf(requirementStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				requirement = LootTier.UnlockRequirement.NONE;
			}

			LootTier tier = new LootTier(tierId, displayName, tierLevel, requirement, unlockItemId,
			                             unlockItemDisplay, floatingItemIcon);
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

			if (minItems < 1) {
				log.warn("Loot table '{}' has Min_Items={} — clamping to 1 so chests never spawn empty",
				         tableId, minItems);
				minItems = 1;
			}

			if (maxItems < minItems) {
				log.warn("Loot table '{}' has Max_Items={} < Min_Items={} — clamping Max_Items to Min_Items",
				         tableId, maxItems, minItems);
				maxItems = minItems;
			}

			// Load rarity overrides for this table
			Map<LootItemReference.Rarity, Double> rarityOverrides = loadTableRarityOverrides(tableSection,
			                                                                                 globalRarities);

			List<LootItemReference> items = loadLootItemReferences(tableSection.getConfigurationSection("Items"));

			LootTable lootTable = new LootTable(tableId, displayName, items, minItems, maxItems, allowedTiers,
			                                    rarityOverrides);

			String problem = lootTable.validate();
			if (problem != null) {
				log.warn("Loot table '{}' is invalid: {} — skipping registration", tableId, problem);
				continue;
			}

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

			// Item string parsed by the global ItemParser (e.g. "weapon:ak47", "ammo:9mm{name=&6Gold Bullets}")
			String itemString = itemSection.getString("Item");
			if (itemString == null || itemString.isBlank()) {
				log.warn("Loot entry '{}' has no 'Item' string — skipping", itemId);
				continue;
			}

			// Drop_Chance scales how often the roll lands on this item
			String                   rarityStr = itemSection.getString("Drop_Chance", "COMMON");
			LootItemReference.Rarity rarity;
			try {
				rarity = LootItemReference.Rarity.valueOf(rarityStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				rarity = LootItemReference.Rarity.COMMON;
			}

			int    minAmount = itemSection.getInt("Min_Amount", 1);
			int    maxAmount = itemSection.getInt("Max_Amount", minAmount);
			double weight    = itemSection.getDouble("Weight", 1.0);

			LootItemReference lootItem = LootItemReference.builder()
			                                              .id(itemId)
			                                              .itemString(itemString)
			                                              .rarity(rarity)
			                                              .minAmount(minAmount)
			                                              .maxAmount(maxAmount)
			                                              .weight(weight)
			                                              .build();

			items.add(lootItem);
		}

		return items;
	}

}
