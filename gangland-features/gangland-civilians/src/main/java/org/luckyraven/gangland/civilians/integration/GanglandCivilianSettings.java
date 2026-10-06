package org.luckyraven.gangland.civilians.integration;

import org.luckyraven.gangland.civilians.npc.config.CivilianSettings;
import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.Objects;

/**
 * Implements {@link CivilianSettings} from the module's own {@code npc/civilians.yml} ({@code Behaviour},
 * {@code Navigation}, {@code Spawner_Proximity}). These keys moved out of the core {@code settings.yml}
 * ({@code Civilians.*}, {@code NPC_Navigation.*}) in 0.15.1; {@link MovedSetting} still honours a customised old value
 * for one release. Parsed in {@link #initialize()} (load and {@code /glw reload}), never per call.
 */
public class GanglandCivilianSettings implements CivilianSettings, FileInitializer {

	private final FileHandler fileHandler;
	private final FileManager fileManager;

	private boolean civilianAiEnabled;
	private int civilianAiTickRate;
	private int npcNavRecalculationTicks;
	private int npcNavStuckCheckInterval;
	private int npcNavMaxStuckChecks;
	private int npcNavMaxHopelessStuckChecks;
	private double npcNavHopelessCloseThreshold;
	private double npcNavMinProgressDistance;
	private double npcNavRangedMinDistance;
	private double npcNavRangedMaxDistance;
	private int npcNavMinRepathAfterLossTicks;
	private double civilianSpawnerActivationRadius;
	private double civilianSpawnerDespawnRadius;
	private int civilianSpawnerMaxNpcs;
	private double civilianSpawnerSoftLeashRadius;
	private double civilianSpawnerHardLeashRadius;
	private int civilianSpawnerCheckInterval;
	private String civilianSpawnerDefaultTypeId;

	public GanglandCivilianSettings(FileManager fileManager) {
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
		civilianAiEnabled = moved.getBoolean("Behaviour.Enabled", "Civilians.Behaviour.Enabled", true);
		civilianAiTickRate = moved.getInt("Behaviour.AI_Tick_Rate", "Civilians.Behaviour.AI_Tick_Rate", 20);
		npcNavRecalculationTicks = moved.getInt("Navigation.Recalculation_Ticks", "NPC_Navigation.Recalculation_Ticks", 10);
		npcNavStuckCheckInterval = moved.getInt("Navigation.Stuck_Check_Interval", "NPC_Navigation.Stuck_Check_Interval", 5);
		npcNavMaxStuckChecks = moved.getInt("Navigation.Max_Stuck_Checks", "NPC_Navigation.Max_Stuck_Checks", 3);
		npcNavMaxHopelessStuckChecks = moved.getInt("Navigation.Max_Hopeless_Stuck_Checks", "NPC_Navigation.Max_Hopeless_Stuck_Checks", 6);
		npcNavHopelessCloseThreshold = moved.getDouble("Navigation.Hopeless_Close_Threshold", "NPC_Navigation.Hopeless_Close_Threshold", 8.0);
		npcNavMinProgressDistance = moved.getDouble("Navigation.Min_Progress_Distance", "NPC_Navigation.Min_Progress_Distance", 0.75);
		npcNavRangedMinDistance = moved.getDouble("Navigation.Ranged_Min_Distance", "NPC_Navigation.Ranged_Min_Distance", 7.0);
		npcNavRangedMaxDistance = moved.getDouble("Navigation.Ranged_Max_Distance", "NPC_Navigation.Ranged_Max_Distance", 12.0);
		npcNavMinRepathAfterLossTicks = moved.getInt("Navigation.Min_Repath_After_Loss_Ticks", "NPC_Navigation.Min_Repath_After_Loss_Ticks", 2);
		civilianSpawnerActivationRadius = moved.getDouble("Spawner_Proximity.Activation_Radius", "Civilians.Spawner_Proximity.Activation_Radius", 60.0);
		civilianSpawnerDespawnRadius = moved.getDouble("Spawner_Proximity.Despawn_Radius", "Civilians.Spawner_Proximity.Despawn_Radius", 80.0);
		civilianSpawnerMaxNpcs = moved.getInt("Spawner_Proximity.Max_Npcs_Per_Spawner", "Civilians.Spawner_Proximity.Max_Npcs_Per_Spawner", 5);
		civilianSpawnerSoftLeashRadius = moved.getDouble("Spawner_Proximity.Npc_Soft_Leash_Radius", "Civilians.Spawner_Proximity.Npc_Soft_Leash_Radius", 30.0);
		civilianSpawnerHardLeashRadius = moved.getDouble("Spawner_Proximity.Npc_Hard_Leash_Radius", "Civilians.Spawner_Proximity.Npc_Hard_Leash_Radius", 50.0);
		civilianSpawnerCheckInterval = moved.getInt("Spawner_Proximity.Check_Interval", "Civilians.Spawner_Proximity.Check_Interval", 100);
		civilianSpawnerDefaultTypeId = moved.getString("Spawner_Proximity.Default_Type_Id", "Civilians.Spawner_Proximity.Default_Type_Id", "");
	}

	@Override
	public boolean isCivilianAiEnabled() {
		return civilianAiEnabled;
	}

	@Override
	public int getCivilianAiTickRate() {
		return civilianAiTickRate;
	}

	// ── NpcNavigationConfig ───────────────────────────────────────────────────

	@Override
	public int getAiTickRate() {
		return civilianAiTickRate;
	}

	@Override
	public int getNavigationRecalculationTicks() {
		return npcNavRecalculationTicks;
	}

	@Override
	public int getStuckCheckIntervalTicks() {
		return npcNavStuckCheckInterval;
	}

	@Override
	public int getMaxStuckChecks() {
		return npcNavMaxStuckChecks;
	}

	@Override
	public int getMaxHopelessStuckChecks() {
		return npcNavMaxHopelessStuckChecks;
	}

	@Override
	public double getHopelessCloseThreshold() {
		return npcNavHopelessCloseThreshold;
	}

	@Override
	public double getMinProgressDistance() {
		return npcNavMinProgressDistance;
	}

	@Override
	public double getRangedMinDistance() {
		return npcNavRangedMinDistance;
	}

	@Override
	public double getRangedMaxDistance() {
		return npcNavRangedMaxDistance;
	}

	@Override
	public int getMinRepathAfterLossTicks() {
		return npcNavMinRepathAfterLossTicks;
	}

	// ── Spawner proximity ─────────────────────────────────────────────────────

	@Override
	public double getCivilianSpawnerActivationRadius() {
		return civilianSpawnerActivationRadius;
	}

	@Override
	public double getCivilianSpawnerDespawnRadius() {
		return civilianSpawnerDespawnRadius;
	}

	@Override
	public int getCivilianSpawnerMaxNpcs() {
		return civilianSpawnerMaxNpcs;
	}

	@Override
	public double getCivilianSpawnerSoftLeashRadius() {
		return civilianSpawnerSoftLeashRadius;
	}

	@Override
	public double getCivilianSpawnerHardLeashRadius() {
		return civilianSpawnerHardLeashRadius;
	}

	@Override
	public int getCivilianSpawnerCheckInterval() {
		return civilianSpawnerCheckInterval;
	}

	@Override
	public String getCivilianSpawnerDefaultTypeId() {
		return civilianSpawnerDefaultTypeId;
	}
}
