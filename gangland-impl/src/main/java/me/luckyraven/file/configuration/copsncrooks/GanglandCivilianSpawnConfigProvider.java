package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.npc.entity.SpawnConfigProvider;
import me.luckyraven.file.configuration.Settings;

/**
 * Implements {@link SpawnConfigProvider} for civilian NPC spawning by delegating to {@link Settings}.
 * <p>
 * This keeps the feature module free from direct dependencies on the plugin's settings implementation.
 */
public class GanglandCivilianSpawnConfigProvider implements SpawnConfigProvider {

	@Override
	public double getMinSpawnDistance() {
		return Settings.getCivilianSpawnMinDistance();
	}

	@Override
	public double getMaxSpawnDistance() {
		return Settings.getCivilianSpawnMaxDistance();
	}

	@Override
	public double getPhase1MinDistance() {
		return Settings.getCivilianSpawnPhase1MinDistance();
	}

	@Override
	public double getSpawnRadiusShrinkStep() {
		return Settings.getCivilianSpawnRadiusShrinkStep();
	}

	@Override
	public int getVerticalSearchRange() {
		return Settings.getCivilianSpawnVerticalSearchRange();
	}

	@Override
	public int getSpawnYOffset() {
		return Settings.getCivilianSpawnYOffset();
	}

	@Override
	public double getMaxSpawnYDiff() {
		return Settings.getCivilianSpawnMaxYDiff();
	}

	@Override
	public double getSpawnerMaxYDiff() {
		return Settings.getCivilianSpawnSpawnerMaxYDiff();
	}

	@Override
	public int getMinOpenHorizontalSides() {
		return Settings.getCivilianSpawnMinOpenHorizontalSides();
	}

	@Override
	public double getSpawnerPreferenceRadius() {
		return Settings.getCivilianSpawnSpawnerPreferenceRadius();
	}

	@Override
	public double getVisibilityCheckDistance() {
		return Settings.getCivilianSpawnVisibilityCheckDistance();
	}

	@Override
	public int getSpawnPhase1Attempts() {
		return Settings.getCivilianSpawnPhase1Attempts();
	}

	@Override
	public int getSpawnPhase2Attempts() {
		return Settings.getCivilianSpawnPhase2Attempts();
	}
}
