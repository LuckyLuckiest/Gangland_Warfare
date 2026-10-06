package org.luckyraven.gangland.civilians.integration;

import org.luckyraven.keystone.npc.entity.SpawnConfigProvider;
import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.Objects;

/**
 * Implements {@link SpawnConfigProvider} for civilian NPC spawning from the {@code Spawn} section of the module's own
 * {@code npc/civilians.yml} (moved out of the core {@code settings.yml} {@code Civilians.Spawn} in 0.15.1;
 * {@link MovedSetting} still honours a customised old value for one release). Parsed in {@link #initialize()}.
 */
public class GanglandCivilianSpawnConfigProvider implements SpawnConfigProvider, FileInitializer {

	private final FileHandler fileHandler;
	private final FileManager fileManager;

	private double civilianSpawnMinDistance;
	private double civilianSpawnMaxDistance;
	private double civilianSpawnPhase1MinDistance;
	private double civilianSpawnRadiusShrinkStep;
	private int civilianSpawnVerticalSearchRange;
	private int civilianSpawnYOffset;
	private double civilianSpawnMaxYDiff;
	private double civilianSpawnSpawnerMaxYDiff;
	private int civilianSpawnMinOpenHorizontalSides;
	private double civilianSpawnSpawnerPreferenceRadius;
	private double civilianSpawnVisibilityCheckDistance;
	private int civilianSpawnPhase1Attempts;
	private int civilianSpawnPhase2Attempts;

	public GanglandCivilianSpawnConfigProvider(FileManager fileManager) {
		this.fileManager = fileManager;
		try {
			fileManager.checkFileLoaded("civilians");
			this.fileHandler = Objects.requireNonNull(fileManager.getFile("civilians"));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		MovedSetting moved = MovedSetting.of(fileHandler, fileManager, "civilians");
		civilianSpawnMinDistance = moved.getDouble("Spawn.Min_Distance", "Civilians.Spawn.Min_Distance", 10.0);
		civilianSpawnMaxDistance = moved.getDouble("Spawn.Max_Distance", "Civilians.Spawn.Max_Distance", 50.0);
		civilianSpawnPhase1MinDistance = moved.getDouble("Spawn.Phase1_Min_Distance", "Civilians.Spawn.Phase1_Min_Distance", 30.0);
		civilianSpawnRadiusShrinkStep = moved.getDouble("Spawn.Radius_Shrink_Step", "Civilians.Spawn.Radius_Shrink_Step", 5.0);
		civilianSpawnVerticalSearchRange = moved.getInt("Spawn.Vertical_Search_Range", "Civilians.Spawn.Vertical_Search_Range", 10);
		civilianSpawnYOffset = moved.getInt("Spawn.Y_Offset", "Civilians.Spawn.Y_Offset", 0);
		civilianSpawnMaxYDiff = moved.getDouble("Spawn.Max_Y_Diff", "Civilians.Spawn.Max_Y_Diff", 4.0);
		civilianSpawnSpawnerMaxYDiff = moved.getDouble("Spawn.Spawner_Max_Y_Diff", "Civilians.Spawn.Spawner_Max_Y_Diff", 16.0);
		civilianSpawnMinOpenHorizontalSides = moved.getInt("Spawn.Min_Open_Sides", "Civilians.Spawn.Min_Open_Sides", 2);
		civilianSpawnSpawnerPreferenceRadius = moved.getDouble("Spawn.Spawner_Preference_Radius", "Civilians.Spawn.Spawner_Preference_Radius", 80.0);
		civilianSpawnVisibilityCheckDistance = moved.getDouble("Spawn.Visibility_Check_Distance", "Civilians.Spawn.Visibility_Check_Distance", 48.0);
		civilianSpawnPhase1Attempts = moved.getInt("Spawn.Phase1_Attempts", "Civilians.Spawn.Phase1_Attempts", 20);
		civilianSpawnPhase2Attempts = moved.getInt("Spawn.Phase2_Attempts", "Civilians.Spawn.Phase2_Attempts", 15);
	}

	@Override
	public double getMinSpawnDistance() {
		return civilianSpawnMinDistance;
	}

	@Override
	public double getMaxSpawnDistance() {
		return civilianSpawnMaxDistance;
	}

	@Override
	public double getPhase1MinDistance() {
		return civilianSpawnPhase1MinDistance;
	}

	@Override
	public double getSpawnRadiusShrinkStep() {
		return civilianSpawnRadiusShrinkStep;
	}

	@Override
	public int getVerticalSearchRange() {
		return civilianSpawnVerticalSearchRange;
	}

	@Override
	public int getSpawnYOffset() {
		return civilianSpawnYOffset;
	}

	@Override
	public double getMaxSpawnYDiff() {
		return civilianSpawnMaxYDiff;
	}

	@Override
	public double getSpawnerMaxYDiff() {
		return civilianSpawnSpawnerMaxYDiff;
	}

	@Override
	public int getMinOpenHorizontalSides() {
		return civilianSpawnMinOpenHorizontalSides;
	}

	@Override
	public double getSpawnerPreferenceRadius() {
		return civilianSpawnSpawnerPreferenceRadius;
	}

	@Override
	public double getVisibilityCheckDistance() {
		return civilianSpawnVisibilityCheckDistance;
	}

	@Override
	public int getSpawnPhase1Attempts() {
		return civilianSpawnPhase1Attempts;
	}

	@Override
	public int getSpawnPhase2Attempts() {
		return civilianSpawnPhase2Attempts;
	}
}
