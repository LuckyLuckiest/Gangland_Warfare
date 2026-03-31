package me.luckyraven.copsncrooks.civilian.config;

import me.luckyraven.copsncrooks.entity.npc.NpcNavigationConfig;

/**
 * Navigation configuration for civilian NPCs, backed by the shared {@code NPC_Navigation} block in {@code settings.yml}
 * via {@link Settings} static getters.
 */
public record CivilianNavigationConfig(
		int aiTickRate,
		int navigationRecalculationTicks,
		int stuckCheckIntervalTicks,
		int maxStuckChecks,
		int maxHopelessStuckChecks,
		double hopelessCloseThreshold,
		double minProgressDistance,
		double rangedMinDistance,
		double rangedMaxDistance,
		int minRepathAfterLossTicks
) implements NpcNavigationConfig {

	/**
	 * Creates a {@link CivilianNavigationConfig} from the given {@link CivilianSettings} provider.
	 */
	public static CivilianNavigationConfig from(CivilianSettings settings) {
		return new CivilianNavigationConfig(
				settings.getCivilianAiTickRate(),
				settings.getNavigationRecalculationTicks(),
				settings.getStuckCheckIntervalTicks(),
				settings.getMaxStuckChecks(),
				settings.getMaxHopelessStuckChecks(),
				settings.getHopelessCloseThreshold(),
				settings.getMinProgressDistance(),
				settings.getRangedMinDistance(),
				settings.getRangedMaxDistance(),
				settings.getMinRepathAfterLossTicks()
		);
	}

	@Override
	public int getAiTickRate() {
		return aiTickRate;
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
}
