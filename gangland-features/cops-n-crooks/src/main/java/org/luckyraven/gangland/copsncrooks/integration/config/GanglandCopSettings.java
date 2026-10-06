package org.luckyraven.gangland.copsncrooks.integration.config;

import net.objecthunter.exp4j.ExpressionBuilder;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksYamlConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopSettings;
import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.Objects;

/**
 * {@link CopSettings} backed by {@code copsncrooks/cops.yml} (0.15.1 settings ownership): {@code Cops.Count},
 * {@code Cops.Behaviour} (plus {@code Guard_Radius}, formerly {@code Detainment.Transit.Guard_Radius}),
 * {@code Cops.Spawn}, {@code Cops.Pursuit}, {@code Cops.Return} and {@code Cops.Navigation} (this module's copy of
 * {@code NPC_Navigation}). Every key is read through {@link MovedSetting}, so a server that still tunes the old
 * {@code settings.yml} key keeps its value for one release. Parsed once per load in {@link #initialize()}, which runs
 * before {@code CopLoader} (registered earlier), and cached.
 * <p>
 * Wanted level maximum is still a core setting and read from {@link Settings}.
 * <p>
 * When formula mode is enabled, {@code Cops.Count.Formula} is evaluated using
 * <a href="https://www.objecthunter.net/exp4j/">exp4j</a> with four variables: {@code level}, {@code base},
 * {@code perLevel}, {@code max}. The result is clamped to {@code [1, max]}. Any evaluation error falls back to the
 * linear formula {@code min(base + (level - 1) * perLevel, max)}, which is also used when formula mode is off.
 */
public class GanglandCopSettings implements CopSettings, FileInitializer {

	static final String FILE_NAME = "cops";

	private static final String COUNT     = "Cops.Count.";
	private static final String BEHAVIOUR = "Cops.Behaviour.";
	private static final String SPAWN     = "Cops.Spawn.";
	private static final String NAV       = "Cops.Navigation.";
	private static final String NAV_OLD   = "NPC_Navigation.";

	private final FileHandler fileHandler;
	private final FileManager fileManager;

	// code defaults until initialize() runs (the same defaults Settings used)
	private boolean countFormulaEnabled = false;
	private String  countFormula        = "base + (level - 1) * perLevel";
	private int     countBase           = 2;
	private int     countPerLevel       = 1;
	private int     countMax            = 8;

	private int    maxCopsPerPlayer    = 8;
	private int    aiTickRate          = 10;
	private int    spawnCheckRate      = 40;
	private double cuffRadius          = 3.0;
	private int    maxCuffAttempts     = 3;
	private int    cuffCooldownTicks   = 100;
	private double alertRange          = 40.0;
	private double combatRange         = 4.0;
	private int    attackCooldownTicks = 20;
	private double guardRadius         = 5.0;

	private double minSpawnDistance        = 10.0;
	private double maxSpawnDistance        = 50.0;
	private double phase1MinDistance       = 30.0;
	private double spawnRadiusShrinkStep   = 5.0;
	private int    verticalSearchRange     = 10;
	private int    spawnYOffset            = 0;
	private int    minOpenHorizontalSides  = 2;
	private double spawnerPreferenceRadius = 80.0;
	private double visibilityCheckDistance = 48.0;
	private int    spawnPhase1Attempts     = 20;
	private int    spawnPhase2Attempts     = 15;
	private double maxSpawnYDiff           = 4.0;
	private double spawnerMaxYDiff         = 16.0;

	private double pursuitMaxDistance     = 80.0;
	private int    pursuitMaxTicks        = 120;
	private int    maxReturnTicks         = 600;
	private double stationArrivalDistance = 3.0;

	private int    navigationRecalculationTicks = 10;
	private int    minRepathAfterLossTicks      = 2;
	private int    stuckCheckIntervalTicks = 5;
	private int    maxStuckChecks          = 3;
	private int    maxHopelessStuckChecks  = 6;
	private double hopelessCloseThreshold  = 8.0;
	private double minProgressDistance     = 0.75;
	private double rangedMinDistance       = 7.0;
	private double rangedMaxDistance       = 12.0;

	public GanglandCopSettings(FileManager fileManager) {
		this.fileManager = fileManager;

		try {
			fileManager.checkFileLoaded(FILE_NAME);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(FILE_NAME));
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
		MovedSetting m = MovedSetting.of(fileHandler, fileManager, CopsNCrooksYamlConfig.MODULE_ID);

		// cops.yml keeps the Cops.* paths of settings.yml, so path and legacy path are the same
		countFormulaEnabled = m.getBoolean(COUNT + "Formula_Enabled", COUNT + "Formula_Enabled", false);
		countFormula        = m.getString(COUNT + "Formula", COUNT + "Formula", "base + (level - 1) * perLevel");
		countBase           = m.getInt(COUNT + "Base", COUNT + "Base", 2);
		countPerLevel       = m.getInt(COUNT + "Per_Level", COUNT + "Per_Level", 1);
		countMax            = m.getInt(COUNT + "Max", COUNT + "Max", 8);

		maxCopsPerPlayer    = m.getInt(BEHAVIOUR + "Max_Per_Player", BEHAVIOUR + "Max_Per_Player", 8);
		aiTickRate          = m.getInt(BEHAVIOUR + "AI_Tick_Rate", BEHAVIOUR + "AI_Tick_Rate", 10);
		spawnCheckRate      = m.getInt(BEHAVIOUR + "Spawn_Check_Rate", BEHAVIOUR + "Spawn_Check_Rate", 40);
		cuffRadius          = m.getDouble(BEHAVIOUR + "Cuff_Radius", BEHAVIOUR + "Cuff_Radius", 3.0);
		maxCuffAttempts     = m.getInt(BEHAVIOUR + "Max_Cuff_Attempts", BEHAVIOUR + "Max_Cuff_Attempts", 3);
		cuffCooldownTicks   = m.getInt(BEHAVIOUR + "Cuff_Cooldown_Ticks", BEHAVIOUR + "Cuff_Cooldown_Ticks", 100);
		alertRange          = m.getDouble(BEHAVIOUR + "Alert_Range", BEHAVIOUR + "Alert_Range", 40.0);
		combatRange         = m.getDouble(BEHAVIOUR + "Combat_Range", BEHAVIOUR + "Combat_Range", 4.0);
		attackCooldownTicks = m.getInt(BEHAVIOUR + "Attack_Cooldown_Ticks", BEHAVIOUR + "Attack_Cooldown_Ticks", 20);
		guardRadius         = m.getDouble(BEHAVIOUR + "Guard_Radius", "Detainment.Transit.Guard_Radius", 5.0);

		minSpawnDistance        = m.getDouble(SPAWN + "Min_Distance", SPAWN + "Min_Distance", 10.0);
		maxSpawnDistance        = m.getDouble(SPAWN + "Max_Distance", SPAWN + "Max_Distance", 50.0);
		phase1MinDistance       = m.getDouble(SPAWN + "Phase1_Min_Distance", SPAWN + "Phase1_Min_Distance", 30.0);
		spawnRadiusShrinkStep   = m.getDouble(SPAWN + "Radius_Shrink_Step", SPAWN + "Radius_Shrink_Step", 5.0);
		verticalSearchRange     = m.getInt(SPAWN + "Vertical_Search_Range", SPAWN + "Vertical_Search_Range", 10);
		spawnYOffset            = m.getInt(SPAWN + "Y_Offset", SPAWN + "Y_Offset", 0);
		minOpenHorizontalSides  = m.getInt(SPAWN + "Min_Open_Sides", SPAWN + "Min_Open_Sides", 2);
		spawnerPreferenceRadius = m.getDouble(SPAWN + "Spawner_Preference_Radius",
		                                      SPAWN + "Spawner_Preference_Radius", 80.0);
		visibilityCheckDistance = m.getDouble(SPAWN + "Visibility_Check_Distance",
		                                      SPAWN + "Visibility_Check_Distance", 48.0);
		spawnPhase1Attempts     = m.getInt(SPAWN + "Phase1_Attempts", SPAWN + "Phase1_Attempts", 20);
		spawnPhase2Attempts     = m.getInt(SPAWN + "Phase2_Attempts", SPAWN + "Phase2_Attempts", 15);
		maxSpawnYDiff           = m.getDouble(SPAWN + "Max_Y_Diff", SPAWN + "Max_Y_Diff", 4.0);
		spawnerMaxYDiff         = m.getDouble(SPAWN + "Spawner_Max_Y_Diff", SPAWN + "Spawner_Max_Y_Diff", 16.0);

		pursuitMaxDistance     = m.getDouble("Cops.Pursuit.Max_Distance", "Cops.Pursuit.Max_Distance", 80.0);
		pursuitMaxTicks        = m.getInt("Cops.Pursuit.Max_Ticks", "Cops.Pursuit.Max_Ticks", 120);
		maxReturnTicks         = m.getInt("Cops.Return.Max_Ticks", "Cops.Return.Max_Ticks", 600);
		stationArrivalDistance = m.getDouble("Cops.Return.Station_Arrival_Distance",
		                                     "Cops.Return.Station_Arrival_Distance", 3.0);

		navigationRecalculationTicks = m.getInt(NAV + "Recalculation_Ticks", NAV_OLD + "Recalculation_Ticks", 10);
		minRepathAfterLossTicks      = m.getInt(NAV + "Min_Repath_After_Loss_Ticks",
		                                        NAV_OLD + "Min_Repath_After_Loss_Ticks", 2);
		stuckCheckIntervalTicks = m.getInt(NAV + "Stuck_Check_Interval", NAV_OLD + "Stuck_Check_Interval", 5);
		maxStuckChecks          = m.getInt(NAV + "Max_Stuck_Checks", NAV_OLD + "Max_Stuck_Checks", 3);
		maxHopelessStuckChecks  = m.getInt(NAV + "Max_Hopeless_Stuck_Checks", NAV_OLD + "Max_Hopeless_Stuck_Checks",
		                                   6);
		hopelessCloseThreshold  = m.getDouble(NAV + "Hopeless_Close_Threshold", NAV_OLD + "Hopeless_Close_Threshold",
		                                      8.0);
		minProgressDistance     = m.getDouble(NAV + "Min_Progress_Distance", NAV_OLD + "Min_Progress_Distance", 0.75);
		rangedMinDistance       = m.getDouble(NAV + "Ranged_Min_Distance", NAV_OLD + "Ranged_Min_Distance", 7.0);
		rangedMaxDistance       = m.getDouble(NAV + "Ranged_Max_Distance", NAV_OLD + "Ranged_Max_Distance", 12.0);
	}

	@Override
	public int getCountForLevel(int level) {
		if (countFormulaEnabled && countFormula != null && !countFormula.isBlank()) {
			Integer count = formulaCalculation(level, countFormula, countBase, countPerLevel, countMax);
			if (count != null) return count;
		}

		return Math.min(countBase + (level - 1) * countPerLevel, countMax);
	}

	@Override
	public int getMaxWantedLevel() {
		return Settings.getWantedMaximumLevel();
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
	public double getMaxSpawnYDiff() {
		return maxSpawnYDiff;
	}

	@Override
	public double getSpawnerMaxYDiff() {
		return spawnerMaxYDiff;
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
	public double getGuardRadius() {
		return guardRadius;
	}

	private Integer formulaCalculation(int level, String formula, int base, int perLevel, int max) {
		try {
			double result = new ExpressionBuilder(formula).variables("level", "base", "perLevel", "max")
			                                              .build()
			                                              .setVariable("level", level)
			                                              .setVariable("base", base)
			                                              .setVariable("perLevel", perLevel)
			                                              .setVariable("max", max)
			                                              .evaluate();

			return Math.max(1, Math.min(max, (int) Math.round(result)));
		} catch (Exception ignored) {
			// fall through to linear formula
		}
		return null;
	}
}
