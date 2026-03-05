package me.luckyraven.copsncrooks.police.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Reads cop configuration from a YAML FileConfiguration.
 */
public class YamlCopConfigProvider implements CopConfigProvider {

	private final Map<Integer, CopTierConfig> tiers;
	private final Map<Integer, Integer>       copsPerWantedLevel;
	private final List<Location>              spawnLocations;
	private final int                         maxCopsPerPlayer;
	private final int                         aiTickRate;
	private final int                         spawnCheckRate;
	private final double                      cuffRadius;
	private final int                         maxCuffAttempts;
	private final int                         cuffCooldownTicks;
	private final double                      alertRange;
	private final double                      combatRange;
	private final int                         attackCooldownTicks;

	private final String head = "Cops.";

	public YamlCopConfigProvider(FileConfiguration config) {
		this.tiers              = new LinkedHashMap<>();
		this.copsPerWantedLevel = new LinkedHashMap<>();
		this.spawnLocations     = new ArrayList<>();

		this.maxCopsPerPlayer    = config.getInt(head + "Max_Per_Player", 8);
		this.aiTickRate          = config.getInt(head + "AI_Tick_Rate", 10);
		this.spawnCheckRate      = config.getInt(head + "Spawn_Check_Rate", 40);
		this.cuffRadius          = config.getDouble(head + "Cuff_Radius", 3.0);
		this.maxCuffAttempts     = config.getInt(head + "Max_Cuff_Attempts", 3);
		this.cuffCooldownTicks   = config.getInt(head + "Cuff_Cooldown_Ticks", 100);
		this.alertRange          = config.getDouble(head + "Alert_Range", 40.0);
		this.combatRange         = config.getDouble(head + "Combat_Range", 4.0);
		this.attackCooldownTicks = config.getInt(head + "Attack_Cooldown_Ticks", 20);

		loadTiers(config);
		loadCopsPerLevel(config);
		loadSpawnLocations(config);
	}

	@Override
	public CopTierConfig getTierConfig(int tier) {
		int clampedTier = Math.min(tier, getMaxTier());
		return tiers.getOrDefault(clampedTier, tiers.get(getMaxTier()));
	}

	@Override
	public int getMaxTier() {
		return tiers.keySet()
				.stream().mapToInt(Integer::intValue).max().orElse(1);
	}

	@Override
	public Map<Integer, Integer> getCopsPerWantedLevel() {
		return Collections.unmodifiableMap(copsPerWantedLevel);
	}

	@Override
	public int getMaxCopsPerPlayer() {
		return maxCopsPerPlayer;
	}

	@Override
	public List<Location> getSpawnLocations() {
		return Collections.unmodifiableList(spawnLocations);
	}

	@Override
	public int getAiTickRate() {
		return aiTickRate;
	}

	@Override
	public int getSpawnCheckRate() {
		return spawnCheckRate;
	}

	@Override
	public double getCuffRadius() {
		return cuffRadius;
	}

	@Override
	public int getMaxCuffAttempts() {
		return maxCuffAttempts;
	}

	@Override
	public int getCuffCooldownTicks() {
		return cuffCooldownTicks;
	}

	@Override
	public double getAlertRange() {
		return alertRange;
	}

	@Override
	public double getCombatRange() {
		return combatRange;
	}

	@Override
	public int getAttackCooldownTicks() {
		return attackCooldownTicks;
	}

	/**
	 * Parses tier definitions from the configuration.
	 *
	 * @param config the file configuration
	 */
	private void loadTiers(FileConfiguration config) {
		ConfigurationSection tiersSection = config.getConfigurationSection(head + "Tiers");
		if (tiersSection == null) return;

		for (String key : tiersSection.getKeys(false)) {
			ConfigurationSection section = tiersSection.getConfigurationSection(key);
			if (section == null) continue;

			int tierNum = Integer.parseInt(key);

			// Keep all entries as weapon names (for gangland-weapon lookup).
			// Also build the vanilla ItemStack pool for any names that are plain Materials.
			List<String>    weaponNamePool = new ArrayList<>(section.getStringList("Weapon_Pool"));
			List<ItemStack> weaponPool     = new ArrayList<>();
			for (String entry : weaponNamePool) {
				try {
					weaponPool.add(new ItemStack(Material.valueOf(entry.toUpperCase())));
				} catch (IllegalArgumentException ignored) {
					// Not a vanilla material — it is a gangland weapon name; skip vanilla pool
				}
			}

			String helmet     = section.getString("Helmet");
			String chestplate = section.getString("Chestplate");
			String leggings   = section.getString("Leggings");
			String boots      = section.getString("Boots");
			var tierConfig = new CopTierConfig(tierNum, section.getString("Display_Name", "&9Police"),
											   section.getDouble("Health", 20.0), section.getDouble("Damage", 2.0),
											   section.getDouble("Speed", 1.0),
											   section.getDouble("Cuff_Radius", cuffRadius),
											   section.getBoolean("Can_Use_Weapons", false), weaponNamePool, weaponPool,
											   parseMaterial(helmet), parseMaterial(chestplate),
											   parseMaterial(leggings), parseMaterial(boots));

			tiers.put(tierNum, tierConfig);
		}
	}

	/**
	 * Parses cops-per-wanted-level mapping.
	 *
	 * @param config the file configuration
	 */
	private void loadCopsPerLevel(FileConfiguration config) {
		ConfigurationSection section = config.getConfigurationSection(head + "Cops_Per_Wanted_Level");
		if (section == null) return;

		for (String key : section.getKeys(false)) {
			copsPerWantedLevel.put(Integer.parseInt(key), section.getInt(key));
		}
	}

	/**
	 * Parses spawn location definitions.
	 *
	 * @param config the file configuration
	 */
	private void loadSpawnLocations(FileConfiguration config) {
		List<Map<?, ?>> locations = config.getMapList(head + "Spawn_Locations");

		for (Map<?, ?> loc : locations) {
			String worldName = String.valueOf(loc.get("World"));
			World  world     = Bukkit.getWorld(worldName);
			if (world == null) continue;

			double x = ((Number) loc.get("X")).doubleValue();
			double y = ((Number) loc.get("Y")).doubleValue();
			double z = ((Number) loc.get("Z")).doubleValue();

			spawnLocations.add(new Location(world, x, y, z));
		}
	}

	/**
	 * Safely parses a material string to an ItemStack.
	 *
	 * @param materialName the material name
	 *
	 * @return the ItemStack or null
	 */
	private ItemStack parseMaterial(String materialName) {
		if (materialName == null || materialName.isEmpty()) return null;
		try {
			return new ItemStack(Material.valueOf(materialName.toUpperCase()));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}