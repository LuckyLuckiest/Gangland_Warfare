package me.luckyraven.weapon.repair.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads and manages repair item configurations from YAML files.
 */
public class RepairItemConfig {

	private final JavaPlugin        plugin;
	private final File              configFile;
	private       FileConfiguration config;

	public RepairItemConfig(@NotNull JavaPlugin plugin) {
		this.plugin     = plugin;
		this.configFile = new File(plugin.getDataFolder(), "repair-items.yml");
	}

	/**
	 * Loads the repair items configuration. Creates a default config if it doesn't exist.
	 */
	public void load() {
		if (!configFile.exists()) {
			createDefaultConfig();
		}

		config = YamlConfiguration.loadConfiguration(configFile);
		plugin.getLogger().info("Loaded repair items configuration from " + configFile.getName());
	}

	/**
	 * Reloads the configuration from disk.
	 */
	public void reload() {
		config = YamlConfiguration.loadConfiguration(configFile);
		plugin.getLogger().info("Reloaded repair items configuration");
	}

	/**
	 * Gets all repair item data from the config.
	 *
	 * @return A map of repair item ID to RepairItemData
	 */
	@NotNull
	public Map<String, RepairItemData> getAllRepairItems() {
		Map<String, RepairItemData> items = new HashMap<>();

		ConfigurationSection repairItemsSection = config.getConfigurationSection("repair-items");
		if (repairItemsSection == null) {
			plugin.getLogger().warning("No 'repair-items' section found in repair-items.yml");
			return items;
		}

		for (String id : repairItemsSection.getKeys(false)) {
			try {
				RepairItemData data = loadRepairItemData(id, repairItemsSection.getConfigurationSection(id));
				if (data != null) {
					items.put(id, data);
				}
			} catch (Exception e) {
				plugin.getLogger().log(Level.SEVERE, "Failed to load repair item: " + id, e);
			}
		}

		plugin.getLogger().info("Loaded " + items.size() + " repair items from configuration");
		return items;
	}

	/**
	 * Saves the current configuration to disk.
	 */
	public void save() {
		try {
			config.save(configFile);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to save repair-items.yml", e);
		}
	}

	public FileConfiguration getConfig() {
		return config;
	}

	/**
	 * Loads a single repair item from configuration.
	 */
	private RepairItemData loadRepairItemData(@NotNull String id, ConfigurationSection section) {
		if (section == null) {
			plugin.getLogger().warning("Invalid configuration section for repair item: " + id);
			return null;
		}

		// Required fields
		String displayName = section.getString("display-name");
		if (displayName == null) {
			plugin.getLogger().warning("Missing 'display-name' for repair item: " + id);
			return null;
		}

		String   materialName = section.getString("material", "PAPER");
		Material material;
		try {
			material = Material.valueOf(materialName.toUpperCase());
		} catch (IllegalArgumentException e) {
			plugin.getLogger().warning("Invalid material '" + materialName + "' for repair item: " + id);
			material = Material.PAPER;
		}

		int level      = section.getInt("level", 1);
		int durability = section.getInt("durability", 1);

		// Optional fields
		List<String> lore = section.getStringList("lore");
		if (lore == null) {
			lore = new ArrayList<>();
		}

		// Load effects
		List<ConfigurationSection> effects     = new ArrayList<>();
		List<?>                    effectsList = section.getList("effects");
		if (effectsList != null) {
			for (Object obj : effectsList) {
				if (obj instanceof Map) {
					// Convert Map to ConfigurationSection
					ConfigurationSection effectSection = section.createSection("temp_" + UUID.randomUUID());
					@SuppressWarnings("unchecked")
					Map<String, Object> effectMap = (Map<String, Object>) obj;
					for (Map.Entry<String, Object> entry : effectMap.entrySet()) {
						effectSection.set(entry.getKey(), entry.getValue());
					}
					effects.add(effectSection);
				}
			}
		}

		// Load metadata
		Map<String, Object>  metadata        = new HashMap<>();
		ConfigurationSection metadataSection = section.getConfigurationSection("metadata");
		if (metadataSection != null) {
			metadata.putAll(metadataSection.getValues(false));
		}

		int customModelData = section.getInt("custom-model-data", 0);

		return RepairItemData.builder()
							 .id(id)
							 .displayName(displayName)
							 .material(material)
							 .level(level)
							 .durability(durability)
							 .lore(lore)
							 .effects(effects)
							 .metadata(metadata)
							 .customModelData(customModelData)
							 .build();
	}

	/**
	 * Creates a default configuration file with example repair items.
	 */
	private void createDefaultConfig() {
		plugin.getDataFolder().mkdirs();

		// Create default config content
		StringBuilder defaultConfig = new StringBuilder();
		defaultConfig.append("# ============================================\n");
		defaultConfig.append("# Weapon Repair Items Configuration\n");
		defaultConfig.append("# ============================================\n");
		defaultConfig.append("#\n");
		defaultConfig.append("# This file defines all repair items available in the server.\n");
		defaultConfig.append("#\n");
		defaultConfig.append("# Repair Item Structure:\n");
		defaultConfig.append("#   id:\n");
		defaultConfig.append("#     display-name: \"Item Name\"\n");
		defaultConfig.append("#     material: MATERIAL_NAME\n");
		defaultConfig.append("#     level: 1                    # Repair item tier/level\n");
		defaultConfig.append("#     durability: 5               # How many uses (1 = single-use)\n");
		defaultConfig.append("#     custom-model-data: 0        # Optional custom model data\n");
		defaultConfig.append("#     lore:\n");
		defaultConfig.append("#       - \"&7Lore line 1\"\n");
		defaultConfig.append("#       - \"&7Lore line 2\"\n");
		defaultConfig.append("#     effects:\n");
		defaultConfig.append("#       - type: effect_type\n");
		defaultConfig.append("#         parameter: value\n");
		defaultConfig.append("#     metadata:                   # Optional custom metadata\n");
		defaultConfig.append("#       key: value\n");
		defaultConfig.append("#\n");
		defaultConfig.append("# Available Effect Types:\n");
		defaultConfig.append("#   - durability_restore: Restores weapon durability\n");
		defaultConfig.append("#       amount: 25 (amount to restore)\n");
		defaultConfig.append("#   - cleaning: Cleans the weapon\n");
		defaultConfig.append("#   - component_fix: Fixes mechanical components\n");
		defaultConfig.append("#\n");
		defaultConfig.append("# ============================================\n\n");

		defaultConfig.append("repair-items:\n\n");

		// Cleaning Kit
		defaultConfig.append("  cleaning_kit:\n");
		defaultConfig.append("    display-name: \"&a&lCleaning Kit\"\n");
		defaultConfig.append("    material: PAPER\n");
		defaultConfig.append("    level: 1\n");
		defaultConfig.append("    durability: 5\n");
		defaultConfig.append("    lore:\n");
		defaultConfig.append("      - \"&7A basic cleaning kit for weapons.\"\n");
		defaultConfig.append("      - \"&7Restores a small amount of durability.\"\n");
		defaultConfig.append("    effects:\n");
		defaultConfig.append("      - type: cleaning\n");
		defaultConfig.append("      - type: durability_restore\n");
		defaultConfig.append("        amount: 15\n\n");

		// Mechanical Part
		defaultConfig.append("  mechanical_part:\n");
		defaultConfig.append("    display-name: \"&6&lMechanical Part\"\n");
		defaultConfig.append("    material: IRON_INGOT\n");
		defaultConfig.append("    level: 3\n");
		defaultConfig.append("    durability: 1\n");
		defaultConfig.append("    lore:\n");
		defaultConfig.append("      - \"&7Advanced mechanical component.\"\n");
		defaultConfig.append("      - \"&7Fixes weapon mechanisms.\"\n");
		defaultConfig.append("    effects:\n");
		defaultConfig.append("      - type: component_fix\n\n");

		// Weapon Repair Kit
		defaultConfig.append("  weapon_repair_kit:\n");
		defaultConfig.append("    display-name: \"&b&lWeapon Repair Kit\"\n");
		defaultConfig.append("    material: DIAMOND\n");
		defaultConfig.append("    level: 5\n");
		defaultConfig.append("    durability: 3\n");
		defaultConfig.append("    lore:\n");
		defaultConfig.append("      - \"&7Professional weapon repair kit.\"\n");
		defaultConfig.append("      - \"&7Fully restores weapon condition.\"\n");
		defaultConfig.append("    effects:\n");
		defaultConfig.append("      - type: durability_restore\n");
		defaultConfig.append("        amount: 100\n");
		defaultConfig.append("      - type: cleaning\n");
		defaultConfig.append("      - type: component_fix\n\n");

		// Basic Repair Tool
		defaultConfig.append("  basic_repair_tool:\n");
		defaultConfig.append("    display-name: \"&7Basic Repair Tool\"\n");
		defaultConfig.append("    material: STICK\n");
		defaultConfig.append("    level: 1\n");
		defaultConfig.append("    durability: 10\n");
		defaultConfig.append("    lore:\n");
		defaultConfig.append("      - \"&7A simple tool for minor repairs.\"\n");
		defaultConfig.append("    effects:\n");
		defaultConfig.append("      - type: durability_restore\n");
		defaultConfig.append("        amount: 5\n");

		try {
			java.nio.file.Files.write(configFile.toPath(), defaultConfig.toString().getBytes());
			plugin.getLogger().info("Created default repair-items.yml");
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Failed to create default repair-items.yml", e);
		}
	}
}