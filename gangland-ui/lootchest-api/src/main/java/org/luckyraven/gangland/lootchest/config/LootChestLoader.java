package org.luckyraven.gangland.lootchest.config;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.exception.PluginException;
import org.luckyraven.gangland.lootchest.LootChestService;
import org.luckyraven.gangland.lootchest.data.LootTable;
import org.luckyraven.gangland.lootchest.data.LootTier;
import org.luckyraven.gangland.lootchest.item.LootItemReference;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.FileLoader;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.config.ConfigReport;
import org.luckyraven.gangland.persistence.config.FileHandlerReader;
import org.luckyraven.gangland.persistence.config.MappingNode;
import org.luckyraven.gangland.persistence.config.NodeReader;

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
		FileHandler lootChestsHandler;
		FileHandler tiersHandler;

		try {
			String lootChests = "loot_chests";
			fileManager.checkFileLoaded(lootChests);
			lootChestsHandler = Objects.requireNonNull(fileManager.getFile(lootChests));

			String tiers = "tiers";
			fileManager.checkFileLoaded(tiers);
			tiersHandler = Objects.requireNonNull(fileManager.getFile(tiers));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		ConfigReport report = new ConfigReport();

		NodeReader lootChestsReader = FileHandlerReader.read(lootChestsHandler, report);
		NodeReader tiersReader      = FileHandlerReader.read(tiersHandler, report);

		Map<String, LootTier>                 tiers               = loadTiers(tiersReader, report);
		Map<LootItemReference.Rarity, Double> globalRarityChances = loadGlobalRaritySettings(tiersReader);
		Map<String, LootTable> lootTables = loadLootTables(lootChestsReader, globalRarityChances,
		                                                   report);

		if (!report.isEmpty()) report.log(log);

		loadedConfig = LootChestConfig.fromProvider(settingsProvider, tiers, lootTables, globalRarityChances);

		manager.setConfig(loadedConfig);

		log.debug("Loaded {} tiers and {} loot tables", tiers.size(), lootTables.size());

		if (consumer != null) {
			consumer.accept(loadedConfig);
		}
	}

	private Map<LootItemReference.Rarity, Double> loadGlobalRaritySettings(NodeReader tiersReader) {
		Map<LootItemReference.Rarity, Double> rarityChances = new EnumMap<>(LootItemReference.Rarity.class);

		MappingNode raritySection = tiersReader.get("Rarity").asMapping().required().orNull();
		if (raritySection == null) return rarityChances;

		NodeReader rarityReader = NodeReader.of(raritySection, tiersReader.report());

		for (LootItemReference.Rarity rarity : LootItemReference.Rarity.values()) {
			String key = rarity.name().toLowerCase();
			if (rarityReader.has(key)) {
				rarityChances.put(rarity, rarityReader.get(key).asDouble().orDefault(0.0));
			}
		}

		return rarityChances;
	}

	private Map<String, LootTier> loadTiers(NodeReader tiersReader, ConfigReport report) {
		Map<String, LootTier> tiers = new LinkedHashMap<>();

		MappingNode tiersSection = tiersReader.get("Tiers").asMapping().required().orNull();
		if (tiersSection == null) {
			LootTier defaultTier = new LootTier("default", "&7Common", 1, LootTier.UnlockRequirement.NONE);
			tiers.put("default", defaultTier);
			return tiers;
		}

		NodeReader tiersMap = NodeReader.of(tiersSection, report);

		for (String tierId : tiersMap.keys()) {
			MappingNode tierSection = tiersMap.get(tierId).asMapping().required().orNull();
			if (tierSection == null) continue;

			NodeReader tier = NodeReader.of(tierSection, report);

			String displayName       = tier.get("Display_Name").asString().required().orDefault(tierId);
			int    tierLevel         = tier.get("Level").asInt().orDefault(0);
			String requirementStr    = tier.get("Unlock_Requirement").asString().orDefault("NONE");
			String unlockItemId      = tier.get("Unlock_Item").asString().orNull();
			String unlockItemDisplay = tier.get("Unlock_Item_Display").asString().orNull();
			String floatingItemIcon  = tier.get("Floating_Item_Icon").asString().orNull();

			LootTier.UnlockRequirement requirement;
			try {
				requirement = LootTier.UnlockRequirement.valueOf(requirementStr.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				requirement = LootTier.UnlockRequirement.NONE;
			}

			LootTier tierObj = new LootTier(tierId, displayName, tierLevel, requirement, unlockItemId,
			                                unlockItemDisplay, floatingItemIcon);
			tiers.put(tierId, tierObj);
		}

		return tiers;
	}

	private Map<String, LootTable> loadLootTables(NodeReader lootChestsReader,
	                                              Map<LootItemReference.Rarity, Double> globalRarities,
	                                              ConfigReport report) {
		Map<String, LootTable> lootTables = new HashMap<>();

		MappingNode tablesSection = lootChestsReader.get("Loot_Tables").asMapping().required().orNull();
		if (tablesSection == null) return lootTables;

		NodeReader tables = NodeReader.of(tablesSection, report);

		for (String tableId : tables.keys()) {
			MappingNode tableSection = tables.get(tableId).asMapping().required().orNull();
			if (tableSection == null) continue;

			NodeReader table = NodeReader.of(tableSection, report);

			String       displayName  = table.get("Display_Name").asString().orDefault(tableId);
			int          minItems     = table.get("Min_Items").asInt().orDefault(1);
			int          maxItems     = table.get("Max_Items").asInt().orDefault(5);
			List<String> allowedTiers = table.get("Allowed_Tiers").asList().ofStrings().orEmpty();

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

			Map<LootItemReference.Rarity, Double> rarityOverrides = loadTableRarityOverrides(table, globalRarities);

			List<LootItemReference> items = loadLootItemReferences(table.get("Items").asMapping().orNull(), report);

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

	private Map<LootItemReference.Rarity, Double> loadTableRarityOverrides(NodeReader tableReader,
	                                                                       Map<LootItemReference.Rarity, Double> globalRarities) {
		Map<LootItemReference.Rarity, Double> overrides = new EnumMap<>(LootItemReference.Rarity.class);
		overrides.putAll(globalRarities);

		MappingNode raritySection = tableReader.get("Rarity_Overrides").asMapping().orNull();
		if (raritySection == null) return overrides;

		NodeReader rarityReader = NodeReader.of(raritySection, tableReader.report());

		for (LootItemReference.Rarity rarity : LootItemReference.Rarity.values()) {
			String key = rarity.name().toLowerCase();
			if (rarityReader.has(key)) {
				overrides.put(rarity, rarityReader.get(key).asDouble().orDefault(0.0));
			}
		}

		return overrides;
	}

	private List<LootItemReference> loadLootItemReferences(MappingNode itemsSection, ConfigReport report) {
		List<LootItemReference> items = new ArrayList<>();

		if (itemsSection == null) return items;

		NodeReader itemsReader = NodeReader.of(itemsSection, report);

		for (String itemId : itemsReader.keys()) {
			MappingNode itemSection = itemsReader.get(itemId).asMapping().orNull();
			if (itemSection == null) continue;

			NodeReader item = NodeReader.of(itemSection, report);

			String itemString = item.get("Item").asString().required().orNull();
			if (itemString == null || itemString.isBlank()) continue;

			String rarityStr = item.get("Drop_Chance").asString().orDefault("COMMON");

			LootItemReference.Rarity rarity;
			try {
				rarity = LootItemReference.Rarity.valueOf(rarityStr.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				rarity = LootItemReference.Rarity.COMMON;
			}

			int    minAmount = item.get("Min_Amount").asInt().min(0).orDefault(1);
			int    maxAmount = item.get("Max_Amount").asInt().min(0).orDefault(minAmount);
			double weight    = item.get("Weight").asDouble().min(0).orDefault(1.0);

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
