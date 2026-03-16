package me.luckyraven.copsncrooks.police.config;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class CopConfig {

	private final Map<Integer, CopTierConfig> tiers;
	private final Map<Integer, Integer>       copsPerWantedLevel;
	private final int                         maxTier;
	private final int                         maxCopsPerPlayer;
	private final int                         aiTickRate;
	private final int                         spawnCheckRate;
	private final double                      cuffRadius;
	private final int                         maxCuffAttempts;
	private final int                         cuffCooldownTicks;
	private final double                      alertRange;
	private final double                      combatRange;
	private final int                         attackCooldownTicks;

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

	public static CopConfig fromProvider(CopConfigProvider provider) {
		return CopConfig.builder()
						.tiers(loadTiers(provider))
						.copsPerWantedLevel(provider.getCopsPerWantedLevel())
						.maxTier(provider.getMaxTier())
						.maxCopsPerPlayer(provider.getMaxCopsPerPlayer())
						.aiTickRate(provider.getAiTickRate())
						.spawnCheckRate(provider.getSpawnCheckRate())
						.cuffRadius(provider.getCuffRadius())
						.maxCuffAttempts(provider.getMaxCuffAttempts())
						.cuffCooldownTicks(provider.getCuffCooldownTicks())
						.alertRange(provider.getAlertRange())
						.combatRange(provider.getCombatRange())
						.attackCooldownTicks(provider.getAttackCooldownTicks())
						.minSpawnDistance(provider.getMinSpawnDistance())
						.maxSpawnDistance(provider.getMaxSpawnDistance())
						.phase1MinDistance(provider.getPhase1MinDistance())
						.spawnRadiusShrinkStep(provider.getSpawnRadiusShrinkStep())
						.verticalSearchRange(provider.getVerticalSearchRange())
						.spawnYOffset(provider.getSpawnYOffset())
						.minOpenHorizontalSides(provider.getMinOpenHorizontalSides())
						.spawnerPreferenceRadius(provider.getSpawnerPreferenceRadius())
						.visibilityCheckDistance(provider.getVisibilityCheckDistance())
						.spawnPhase1Attempts(provider.getSpawnPhase1Attempts())
						.spawnPhase2Attempts(provider.getSpawnPhase2Attempts())
						.navigationRecalculationTicks(provider.getNavigationRecalculationTicks())
						.stuckCheckIntervalTicks(provider.getStuckCheckIntervalTicks())
						.maxStuckChecks(provider.getMaxStuckChecks())
						.maxHopelessStuckChecks(provider.getMaxHopelessStuckChecks())
						.hopelessCloseThreshold(provider.getHopelessCloseThreshold())
						.minProgressDistance(provider.getMinProgressDistance())
						.rangedMinDistance(provider.getRangedMinDistance())
						.rangedMaxDistance(provider.getRangedMaxDistance())
						.maxReturnTicks(provider.getMaxReturnTicks())
						.stationArrivalDistance(provider.getStationArrivalDistance())
						.startingAmmoMagazines(provider.getStartingAmmoMagazines())
						.build();
	}

	private static Map<Integer, CopTierConfig> loadTiers(CopConfigProvider provider) {
		Map<Integer, CopTierConfig> tiers = new java.util.LinkedHashMap<>();

		for (int tier = 1; tier <= provider.getMaxTier(); tier++) {
			tiers.put(tier, provider.getTierConfig(tier));
		}

		return tiers;
	}
}
