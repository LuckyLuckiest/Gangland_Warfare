package me.luckyraven.copsncrooks.police.config;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.Location;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class CopConfig {

	private final Map<Integer, CopTierConfig> tiers;
	private final Map<Integer, Integer>       copsPerWantedLevel;
	private final int                         maxTier;
	private final int                         maxCopsPerPlayer;
	private final List<Location>              spawnLocations;
	private final int                         aiTickRate;
	private final int                         spawnCheckRate;
	private final double                      cuffRadius;
	private final int                         maxCuffAttempts;
	private final int                         cuffCooldownTicks;
	private final double                      alertRange;
	private final double                      combatRange;
	private final int                         attackCooldownTicks;

	public static CopConfig fromProvider(CopConfigProvider provider) {
		return CopConfig.builder()
						.tiers(loadTiers(provider))
						.copsPerWantedLevel(provider.getCopsPerWantedLevel())
						.maxTier(provider.getMaxTier())
						.maxCopsPerPlayer(provider.getMaxCopsPerPlayer())
						.spawnLocations(provider.getSpawnLocations())
						.aiTickRate(provider.getAiTickRate())
						.spawnCheckRate(provider.getSpawnCheckRate())
						.cuffRadius(provider.getCuffRadius())
						.maxCuffAttempts(provider.getMaxCuffAttempts())
						.cuffCooldownTicks(provider.getCuffCooldownTicks())
						.alertRange(provider.getAlertRange())
						.combatRange(provider.getCombatRange())
						.attackCooldownTicks(provider.getAttackCooldownTicks())
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
