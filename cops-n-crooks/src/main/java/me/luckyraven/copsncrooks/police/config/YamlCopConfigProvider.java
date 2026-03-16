package me.luckyraven.copsncrooks.police.config;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.util.item.ItemParser;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Reads cop configuration from a YAML {@link FileConfiguration}.
 * <p>
 * Armor and weapon-pool entries are resolved through the shared {@link ItemParser} so that custom item syntax
 * ({@code weapon:rifle}, {@code LEATHER_HELMET\{color=blue\}}, etc.) is supported in addition to plain vanilla material
 * names. Cop-count scaling is delegated to {@link CopSettings}, whose implementation lives in {@code gangland-impl} and
 * reads from {@code settings.yml} via {@code SettingAddon}.
 */
public class YamlCopConfigProvider implements CopConfigProvider {

	private final Map<Integer, CopTierConfig> tiers;
	private final Map<Integer, Integer>       copsPerWantedLevel;

	private final int    maxCopsPerPlayer;
	private final int    aiTickRate;
	private final int    spawnCheckRate;
	private final double cuffRadius;
	private final int    maxCuffAttempts;
	private final int    cuffCooldownTicks;
	private final double alertRange;
	private final double combatRange;
	private final int    attackCooldownTicks;

	// Spawn settings
	private final double minSpawnDistance;
	private final double maxSpawnDistance;
	private final double phase1MinDistance;
	private final double spawnRadiusShrinkStep;
	private final int    verticalSearchRange;
	private final int    spawnYOffset;
	private final int    minOpenHorizontalSides;
	private final double spawnerPreferenceRadius;
	private final double visibilityCheckDistance;
	private final int    spawnPhase1Attempts;
	private final int    spawnPhase2Attempts;

	// Navigation settings
	private final int    navigationRecalculationTicks;
	private final int    stuckCheckIntervalTicks;
	private final int    maxStuckChecks;
	private final int    maxHopelessStuckChecks;
	private final double hopelessCloseThreshold;
	private final double minProgressDistance;
	private final double rangedMinDistance;
	private final double rangedMaxDistance;

	// Return / despawn settings
	private final int    maxReturnTicks;
	private final double stationArrivalDistance;

	// Misc
	private final int startingAmmoMagazines;

	private final String head = "Cops.";

	/**
	 * @param copsConfig the {@code cops.yml} configuration
	 * @param copSettings cop-count-per-wanted-level provider (may be {@code null}; linear defaults are used)
	 * @param itemParser item parser for weapon pool and armor entries (may be {@code null}; falls back to plain
	 * 		Material parsing)
	 */
	public YamlCopConfigProvider(FileConfiguration copsConfig, @Nullable CopSettings copSettings,
								 @Nullable ItemParser itemParser) {
		this.tiers              = new LinkedHashMap<>();
		this.copsPerWantedLevel = new LinkedHashMap<>();

		this.maxCopsPerPlayer    = copSettings != null ? copSettings.getMaxCopsPerPlayer() : 8;
		this.aiTickRate          = copSettings != null ? copSettings.getAiTickRate() : 10;
		this.spawnCheckRate      = copSettings != null ? copSettings.getSpawnCheckRate() : 40;
		this.cuffRadius          = copSettings != null ? copSettings.getCuffRadius() : 3.0;
		this.maxCuffAttempts     = copSettings != null ? copSettings.getMaxCuffAttempts() : 3;
		this.cuffCooldownTicks   = copSettings != null ? copSettings.getCuffCooldownTicks() : 100;
		this.alertRange          = copSettings != null ? copSettings.getAlertRange() : 40.0;
		this.combatRange         = copSettings != null ? copSettings.getCombatRange() : 4.0;
		this.attackCooldownTicks = copSettings != null ? copSettings.getAttackCooldownTicks() : 20;

		// Spawn settings — sourced from settings.yml via CopSettings (falls back to sensible defaults)
		this.minSpawnDistance        = copSettings != null ? copSettings.getMinSpawnDistance() : 10.0;
		this.maxSpawnDistance        = copSettings != null ? copSettings.getMaxSpawnDistance() : 50.0;
		this.phase1MinDistance       = copSettings != null ? copSettings.getPhase1MinDistance() : 30.0;
		this.spawnRadiusShrinkStep   = copSettings != null ? copSettings.getSpawnRadiusShrinkStep() : 5.0;
		this.verticalSearchRange     = copSettings != null ? copSettings.getVerticalSearchRange() : 10;
		this.spawnYOffset            = copSettings != null ? copSettings.getSpawnYOffset() : 0;
		this.minOpenHorizontalSides  = copSettings != null ? copSettings.getMinOpenHorizontalSides() : 2;
		this.spawnerPreferenceRadius = copSettings != null ? copSettings.getSpawnerPreferenceRadius() : 80.0;
		this.visibilityCheckDistance = copSettings != null ? copSettings.getVisibilityCheckDistance() : 48.0;
		this.spawnPhase1Attempts     = copSettings != null ? copSettings.getSpawnPhase1Attempts() : 20;
		this.spawnPhase2Attempts     = copSettings != null ? copSettings.getSpawnPhase2Attempts() : 15;

		// Navigation settings — sourced from settings.yml via CopSettings
		this.navigationRecalculationTicks = copSettings != null ? copSettings.getNavigationRecalculationTicks() : 10;
		this.stuckCheckIntervalTicks      = copSettings != null ? copSettings.getStuckCheckIntervalTicks() : 5;
		this.maxStuckChecks               = copSettings != null ? copSettings.getMaxStuckChecks() : 3;
		this.maxHopelessStuckChecks       = copSettings != null ? copSettings.getMaxHopelessStuckChecks() : 6;
		this.hopelessCloseThreshold       = copSettings != null ? copSettings.getHopelessCloseThreshold() : 8.0;
		this.minProgressDistance          = copSettings != null ? copSettings.getMinProgressDistance() : 0.75;
		this.rangedMinDistance            = copSettings != null ? copSettings.getRangedMinDistance() : 7.0;
		this.rangedMaxDistance            = copSettings != null ? copSettings.getRangedMaxDistance() : 12.0;

		// Return / despawn settings — sourced from settings.yml via CopSettings
		this.maxReturnTicks         = copSettings != null ? copSettings.getMaxReturnTicks() : 600;
		this.stationArrivalDistance = copSettings != null ? copSettings.getStationArrivalDistance() : 3.0;

		// Misc — sourced from settings.yml via CopSettings
		this.startingAmmoMagazines = copSettings != null ? copSettings.getStartingAmmoMagazines() : 3;

		loadTiers(copsConfig, itemParser);
		buildCopsPerWantedLevel(copSettings);
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

	@Override
	public double getMinSpawnDistance() { return minSpawnDistance; }

	@Override
	public double getMaxSpawnDistance() { return maxSpawnDistance; }

	@Override
	public double getPhase1MinDistance() { return phase1MinDistance; }

	@Override
	public double getSpawnRadiusShrinkStep() { return spawnRadiusShrinkStep; }

	@Override
	public int getVerticalSearchRange() { return verticalSearchRange; }

	@Override
	public int getSpawnYOffset() { return spawnYOffset; }

	@Override
	public int getMinOpenHorizontalSides() { return minOpenHorizontalSides; }

	@Override
	public double getSpawnerPreferenceRadius() { return spawnerPreferenceRadius; }

	@Override
	public double getVisibilityCheckDistance() { return visibilityCheckDistance; }

	@Override
	public int getSpawnPhase1Attempts() { return spawnPhase1Attempts; }

	@Override
	public int getSpawnPhase2Attempts() { return spawnPhase2Attempts; }

	@Override
	public int getNavigationRecalculationTicks() { return navigationRecalculationTicks; }

	@Override
	public int getStuckCheckIntervalTicks() { return stuckCheckIntervalTicks; }

	@Override
	public int getMaxStuckChecks() { return maxStuckChecks; }

	@Override
	public int getMaxHopelessStuckChecks() { return maxHopelessStuckChecks; }

	@Override
	public double getHopelessCloseThreshold() { return hopelessCloseThreshold; }

	@Override
	public double getMinProgressDistance() { return minProgressDistance; }

	@Override
	public double getRangedMinDistance() { return rangedMinDistance; }

	@Override
	public double getRangedMaxDistance() { return rangedMaxDistance; }

	@Override
	public int getMaxReturnTicks() { return maxReturnTicks; }

	@Override
	public double getStationArrivalDistance() { return stationArrivalDistance; }

	@Override
	public int getStartingAmmoMagazines() { return startingAmmoMagazines; }

	// -------------------------------------------------------------------------
	// Private loading helpers
	// -------------------------------------------------------------------------

	/**
	 * Parses all tier sections from {@code cops.yml}.
	 * <p>
	 * Weapon-pool entries are routed through {@code itemParser} when available:
	 * <ul>
	 *   <li>{@code weapon:rifle} → weapon name {@code rifle} added to {@code weaponNamePool}; no ItemStack
	 *       needed because the gangland weapon system builds its own item.</li>
	 *   <li>{@code IRON_SWORD}, {@code LEATHER_HELMET\{color=blue\}}, etc. → parsed to an {@link ItemStack} and
	 *       added to {@code weaponPool} for vanilla equipping; the raw entry is also kept in
	 *       {@code weaponNamePool} so {@code WeaponService} can attempt a gangland lookup (it returns
	 *       {@code null} for plain materials, which is handled gracefully).</li>
	 * </ul>
	 * Armor slots use the same parser path and therefore also accept custom item syntax.
	 */
	private void loadTiers(FileConfiguration config, @Nullable ItemParser itemParser) {
		ConfigurationSection tiersSection = config.getConfigurationSection(head + "Tiers");
		if (tiersSection == null) return;

		for (String key : tiersSection.getKeys(false)) {
			ConfigurationSection section = tiersSection.getConfigurationSection(key);
			if (section == null) continue;

			int tierNum = Integer.parseInt(key);

			List<String>    weaponNamePool = new ArrayList<>();
			List<ItemStack> weaponPool     = new ArrayList<>();

			for (String entry : section.getStringList("Weapon_Pool")) {
				if (entry == null || entry.isBlank()) continue;

				if (entry.toLowerCase().startsWith("weapon:")) {
					// Gangland weapon - only the ID after the prefix is needed for WeaponService lookup
					String weaponId = entry.substring("weapon:".length()).trim();
					weaponNamePool.add(weaponId);
				} else {
					// Vanilla material (or ItemParser-extended syntax)
					weaponNamePool.add(entry);
					ItemStack parsed = parseItem(entry, itemParser);
					if (parsed != null) weaponPool.add(parsed);
				}
			}

			var tierConfig = new CopTierConfig(tierNum, section.getString("Display_Name", "&9Police"),
											   section.getDouble("Health", 20.0), section.getDouble("Damage", 2.0),
											   section.getDouble("Speed", 1.0),
											   section.getDouble("Cuff_Radius", cuffRadius),
											   section.getBoolean("Can_Use_Weapons", false),
											   section.getBoolean("Skip_Cuffing", false), weaponNamePool, weaponPool,
											   parseItem(section.getString("Helmet"), itemParser),
											   parseItem(section.getString("Chestplate"), itemParser),
											   parseItem(section.getString("Leggings"), itemParser),
											   parseItem(section.getString("Boots"), itemParser));

			tiers.put(tierNum, tierConfig);
		}
	}

	/**
	 * Builds the cops-per-wanted-level map by delegating to {@link CopSettings}.
	 * <p>
	 * When {@code copCountSettings} is {@code null} (e.g. in unit tests without a DI context), a sensible linear
	 * fallback is used: {@code min(1 + level, maxCopsPerPlayer)} for levels 1–5.
	 */
	private void buildCopsPerWantedLevel(@Nullable CopSettings copSettings) {
		if (copSettings == null) {
			for (int level = 1; level <= 5; level++) {
				copsPerWantedLevel.put(level, Math.min(1 + level, maxCopsPerPlayer));
			}
			return;
		}

		for (int level = 1; level <= copSettings.getMaxWantedLevel(); level++) {
			copsPerWantedLevel.put(level, copSettings.getCountForLevel(level));
		}
	}

	/**
	 * Parses a single item string. Delegates to {@link ItemParser} when available, otherwise falls back to a plain
	 * {@link Material#valueOf} lookup.
	 */
	@Nullable
	private ItemStack parseItem(@Nullable String entry, @Nullable ItemParser itemParser) {
		if (entry == null || entry.isBlank()) return null;

		if (itemParser != null) {
			return itemParser.parse(entry);
		}

		try {
			Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(entry.toUpperCase());
			if (xMaterial.isPresent()) {
				Material mat = xMaterial.get().get();
				if (mat != null) return new ItemStack(mat);
			}

			return new ItemStack(Material.STICK);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
