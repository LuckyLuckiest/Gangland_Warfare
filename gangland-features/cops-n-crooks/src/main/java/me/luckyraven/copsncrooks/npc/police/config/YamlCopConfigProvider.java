package me.luckyraven.copsncrooks.npc.police.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import me.luckyraven.copsncrooks.npc.NpcDifficulty;
import me.luckyraven.item.ItemParser;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.MappingNode;
import me.luckyraven.persistence.config.NodeReader;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Reads cop configuration from a positional {@link NodeReader}.
 * <p>
 * Armor and weapon-pool entries are resolved through the shared {@link ItemParser} so that custom item syntax
 * ({@code weapon:rifle}, {@code LEATHER_HELMET\{color=blue\}}, etc.) is supported in addition to plain vanilla material
 * names. Cop-count scaling is delegated to {@link CopSettings}, whose implementation lives in {@code gangland-impl} and
 * reads from {@code settings.yml} via {@code SettingAddon}.
 */
@CustomLog
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
	private final int    minRepathAfterLossTicks;

	// Pursuit leash settings
	private final double pursuitMaxDistance;
	private final int    pursuitMaxTicks;

	// Return / despawn settings
	private final int    maxReturnTicks;
	private final double stationArrivalDistance;

	// Misc
	private final int    startingAmmoMagazines;
	private final double guardRadius;

	/**
	 * Primary positional-config constructor.
	 *
	 * @param copsReader positional reader over the cops.yml root mapping
	 * @param report issue collector drained by the enclosing loader
	 * @param copSettings cop-count-per-wanted-level provider (may be {@code null})
	 * @param itemParser item parser for weapon pool and armor entries (may be {@code null})
	 */
	public YamlCopConfigProvider(NodeReader copsReader, ConfigReport report,
	                             @Nullable CopSettings copSettings, @Nullable ItemParser itemParser) {
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

		this.navigationRecalculationTicks = copSettings != null ? copSettings.getNavigationRecalculationTicks() : 10;
		this.stuckCheckIntervalTicks      = copSettings != null ? copSettings.getStuckCheckIntervalTicks() : 5;
		this.maxStuckChecks               = copSettings != null ? copSettings.getMaxStuckChecks() : 3;
		this.maxHopelessStuckChecks       = copSettings != null ? copSettings.getMaxHopelessStuckChecks() : 6;
		this.hopelessCloseThreshold       = copSettings != null ? copSettings.getHopelessCloseThreshold() : 8.0;
		this.minProgressDistance          = copSettings != null ? copSettings.getMinProgressDistance() : 0.75;
		this.rangedMinDistance            = copSettings != null ? copSettings.getRangedMinDistance() : 7.0;
		this.rangedMaxDistance            = copSettings != null ? copSettings.getRangedMaxDistance() : 12.0;
		this.minRepathAfterLossTicks      = copSettings != null ? copSettings.getMinRepathAfterLossTicks() : 2;

		this.pursuitMaxDistance = copSettings != null ? copSettings.getPursuitMaxDistance() : 80.0;
		this.pursuitMaxTicks    = copSettings != null ? copSettings.getPursuitMaxTicks() : 120;

		this.maxReturnTicks         = copSettings != null ? copSettings.getMaxReturnTicks() : 600;
		this.stationArrivalDistance = copSettings != null ? copSettings.getStationArrivalDistance() : 3.0;

		this.startingAmmoMagazines = copSettings != null ? copSettings.getStartingAmmoMagazines() : 3;
		this.guardRadius           = copSettings != null ? copSettings.getGuardRadius() : 5.0;

		loadTiers(copsReader, report, itemParser);
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
	public double getMinSpawnDistance() {
		return minSpawnDistance;
	}

	@Override
	public double getMaxSpawnDistance() {
		return maxSpawnDistance;
	}

	@Override
	public double getPhase1MinDistance() {
		return phase1MinDistance;
	}

	@Override
	public double getSpawnRadiusShrinkStep() {
		return spawnRadiusShrinkStep;
	}

	@Override
	public int getVerticalSearchRange() {
		return verticalSearchRange;
	}

	@Override
	public int getSpawnYOffset() {
		return spawnYOffset;
	}

	@Override
	public int getMinOpenHorizontalSides() {
		return minOpenHorizontalSides;
	}

	@Override
	public double getSpawnerPreferenceRadius() {
		return spawnerPreferenceRadius;
	}

	@Override
	public double getVisibilityCheckDistance() {
		return visibilityCheckDistance;
	}

	@Override
	public int getSpawnPhase1Attempts() {
		return spawnPhase1Attempts;
	}

	@Override
	public int getSpawnPhase2Attempts() {
		return spawnPhase2Attempts;
	}

	@Override
	public int getNavigationRecalculationTicks() {
		return navigationRecalculationTicks;
	}

	@Override
	public int getStuckCheckIntervalTicks() {
		return stuckCheckIntervalTicks;
	}

	@Override
	public int getMaxStuckChecks() {
		return maxStuckChecks;
	}

	@Override
	public int getMaxHopelessStuckChecks() {
		return maxHopelessStuckChecks;
	}

	@Override
	public double getHopelessCloseThreshold() {
		return hopelessCloseThreshold;
	}

	@Override
	public double getMinProgressDistance() {
		return minProgressDistance;
	}

	@Override
	public double getRangedMinDistance() {
		return rangedMinDistance;
	}

	@Override
	public double getRangedMaxDistance() {
		return rangedMaxDistance;
	}

	@Override
	public int getMinRepathAfterLossTicks() {
		return minRepathAfterLossTicks;
	}

	@Override
	public double getPursuitMaxDistance() {
		return pursuitMaxDistance;
	}

	@Override
	public int getPursuitMaxTicks() {
		return pursuitMaxTicks;
	}

	@Override
	public int getMaxReturnTicks() {
		return maxReturnTicks;
	}

	@Override
	public double getStationArrivalDistance() {
		return stationArrivalDistance;
	}

	@Override
	public int getStartingAmmoMagazines() {
		return startingAmmoMagazines;
	}

	@Override
	public double getGuardRadius() {
		return guardRadius;
	}

	private void loadTiers(NodeReader copsReader, ConfigReport report, @Nullable ItemParser itemParser) {
		MappingNode copsSection = copsReader.get("Cops").asMapping().required().orNull();
		if (copsSection == null) return;

		NodeReader cops = NodeReader.of(copsSection, report);

		MappingNode tiersSection = cops.get("Tiers").asMapping().required().orNull();
		if (tiersSection == null) return;

		NodeReader tiersReader = NodeReader.of(tiersSection, report);

		for (String key : tiersReader.keys()) {
			MappingNode tierNode = tiersReader.get(key).asMapping().required().orNull();
			if (tierNode == null) continue;

			int tierNum;
			try {
				tierNum = Integer.parseInt(key);
			} catch (NumberFormatException e) {
				log.warn("Cop tier key '{}' is not an integer — skipping", key);
				continue;
			}

			NodeReader tier = NodeReader.of(tierNode, report);

			List<String>    weaponNamePool = new ArrayList<>();
			List<ItemStack> weaponPool     = new ArrayList<>();

			for (String entry : tier.get("Weapon_Pool").asList().ofStrings().orEmpty()) {
				if (entry == null || entry.isBlank()) continue;

				if (entry.toLowerCase(Locale.ROOT).startsWith("weapon:")) {
					weaponNamePool.add(entry.substring("weapon:".length()).trim());
				} else {
					weaponNamePool.add(entry);
					ItemStack parsed = parseItem(entry, itemParser);
					if (parsed != null) weaponPool.add(parsed);
				}
			}

			String difficultyStr = tier.get("Difficulty").asString().orNull();

			MappingNode wearSection = tier.get("Wearables").asMapping().orNull();
			NodeReader  wear        = wearSection != null ? NodeReader.of(wearSection, report) : null;

			CopTierConfig tierConfig = new CopTierConfig(
					tierNum,
					tier.get("Display_Name").asString().required().orDefault("&9Police"),
					tier.get("Health").asDouble().min(0).required().orDefault(20.0),
					tier.get("Damage").asDouble().min(0).required().orDefault(2.0),
					tier.get("Speed").asDouble().min(0).orDefault(1.0),
					tier.get("Cuff_Radius").asDouble().min(0).orDefault(cuffRadius),
					tier.get("Can_Use_Weapons").asBool().orDefault(false),
					tier.get("Skip_Cuffing").asBool().orDefault(false),
					weaponNamePool, weaponPool,
					parseItem(wear == null ? null : wear.get("Helmet").asString().orNull(), itemParser),
					parseItem(wear == null ? null : wear.get("Chestplate").asString().orNull(), itemParser),
					parseItem(wear == null ? null : wear.get("Leggings").asString().orNull(), itemParser),
					parseItem(wear == null ? null : wear.get("Boots").asString().orNull(), itemParser),
					parseDifficulty(difficultyStr, "tier " + tierNum));

			tiers.put(tierNum, tierConfig);
		}
	}

	private NpcDifficulty parseDifficulty(@Nullable String raw, String contextLabel) {
		if (raw == null || raw.isBlank()) return NpcDifficulty.NORMAL;
		try {
			return NpcDifficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			log.warn("Unknown NPC difficulty '{}' for {} — defaulting to NORMAL.", raw, contextLabel);
			return NpcDifficulty.NORMAL;
		}
	}

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

	@Nullable
	private ItemStack parseItem(@Nullable String entry, @Nullable ItemParser itemParser) {
		if (entry == null || entry.isBlank()) return null;

		if (itemParser != null) return itemParser.parse(entry);

		try {
			Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(entry.toUpperCase(Locale.ROOT));
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
