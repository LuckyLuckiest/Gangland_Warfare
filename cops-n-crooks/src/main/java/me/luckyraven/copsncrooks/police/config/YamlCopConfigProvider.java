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

		this.maxCopsPerPlayer    = copsConfig.getInt(head + "Max_Per_Player", 8);
		this.aiTickRate          = copsConfig.getInt(head + "AI_Tick_Rate", 10);
		this.spawnCheckRate      = copsConfig.getInt(head + "Spawn_Check_Rate", 40);
		this.cuffRadius          = copsConfig.getDouble(head + "Cuff_Radius", 3.0);
		this.maxCuffAttempts     = copsConfig.getInt(head + "Max_Cuff_Attempts", 3);
		this.cuffCooldownTicks   = copsConfig.getInt(head + "Cuff_Cooldown_Ticks", 100);
		this.alertRange          = copsConfig.getDouble(head + "Alert_Range", 40.0);
		this.combatRange         = copsConfig.getDouble(head + "Combat_Range", 4.0);
		this.attackCooldownTicks = copsConfig.getInt(head + "Attack_Cooldown_Ticks", 20);

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
			XMaterial type = XMaterial.valueOf(entry.toUpperCase());
			return new ItemStack(Objects.requireNonNull(type.get()));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
