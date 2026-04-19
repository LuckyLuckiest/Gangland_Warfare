package me.luckyraven.file.configuration;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileInitializer;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.FileHandlerReader;
import me.luckyraven.persistence.config.MappingNode;
import me.luckyraven.persistence.config.NodeReader;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@CustomLog
public class Settings implements FileInitializer {

	private static final @Getter Map<String, Object> settingsMap         = new LinkedHashMap<>();
	private static final @Getter Map<String, Object> settingsPlaceholder = new LinkedHashMap<>();

	private static         FileConfiguration settings;
	// update configuration
	private static @Getter boolean           updaterEnabled, notifyPrivilegedPlayers, updaterAutoUpdate;
	// language picked
	private static @Getter String  languagePicked;
	// resource pack
	private static @Getter boolean resourcePackEnabled;
	private static @Getter String  resourcePackUrl;
	private static @Getter boolean resourcePackKick;
	// database configuration
	private static @Getter String  databaseType;
	private static @Getter String  mysqlHost, mysqlUsername, mysqlPassword;
	private static @Getter int     mysqlPort;
	private static @Getter boolean sqliteBackup, sqliteFailedMysql, autoSave, autoSaveDebug;
	private static @Getter int    autoSaveTime;
	private static @Getter double cleanUpTime;
	// inventory configuration
	private static @Getter String inventoryFillItem, inventoryFillName, inventoryLineItem, inventoryLineName, nextPage,
			previousPage, homePage;
	// economy
	private static @Getter String moneySymbol, balanceFormat;
	// user configuration
	private static @Getter double userInitialBalance, userMaxBalance;
	private static @Getter double bankInitialBalance, bankCreateFee, bankMaxBalance;
	// user levels
	private static @Getter int userMaxLevel, userLevelBaseAmount;
	private static @Getter String userLevelFormula;
	private static @Getter int    userSkillUpgrade;
	private static @Getter double userSkillCost, userSkillExponential;
	// user death
	private static @Getter boolean deathEnabled, deathMoneyCommandEnabled, deathLoseMoney;
	private static @Getter List<String> deathMoneyCommandExecutables;
	private static @Getter String       deathLoseMoneyFormula;
	private static @Getter double       deathThreshold;
	// respawn
	private static @Getter boolean      respawnEnabled;
	private static @Getter int          respawnDelay;
	private static @Getter boolean      respawnScreenEnabled;
	private static @Getter String       respawnScreenTitle, respawnScreenSubtitle;
	private static @Getter boolean respawnGameModeAllowFly;
	private static @Getter String  respawnGameMode;
	private static @Getter double  respawnHealthAmount;
	private static @Getter int     respawnHungerAmount;
	private static @Getter boolean respawnTeleportEnabled;
	private static @Getter String  respawnTeleportWaypoint;
	// bounty configuration
	private static @Getter double  bountyEachKillValue, bountyMaxKill;
	private static @Getter boolean bountyTimerEnabled;
	private static @Getter double  bountyTimerMultiple, bountyTimerMax;
	private static @Getter int    bountyTimeInterval;
	// wanted configuration
	private static @Getter double wantedTakeMoneyAmount, wantedTakeMoneyMultiplier, wantedTimerMultiplierAmount;
	private static @Getter boolean wantedEnabled, wantedTimerEnabled, wantedTimerMultiplierEnabled,
			wantedKillComboEnabled;
	private static @Getter int wantedTimerTime, wantedLevelIncrement, wantedMaximumLevel, wantedKillComboResetAfter;
	private static @Getter List<Integer> wantedKillCounter;
	// gang configuration
	private static @Getter boolean       gangEnabled, gangNameDuplicates;
	private static @Getter String gangRankHead, gangRankTail, gangDisplayNameChar;
	private static @Getter double gangInitialBalance, gangCreateFee, gangMaxBalance, gangContributionRate;
	// scoreboard configuration
	private static @Getter boolean scoreboardEnabled;
	private static @Getter String  scoreboardDriver;
	// civilian AI configuration
	private static @Getter boolean civilianAiEnabled;
	private static @Getter int     civilianAiTickRate;
	// civilian spawner proximity configuration
	private static @Getter double  civilianSpawnerActivationRadius;
	private static @Getter double  civilianSpawnerDespawnRadius;
	private static @Getter int     civilianSpawnerMaxNpcs;
	private static @Getter int     civilianSpawnerCheckInterval;
	private static @Getter String  civilianSpawnerDefaultTypeId;
	// civilian spawn configuration
	private static @Getter double  civilianSpawnMinDistance, civilianSpawnMaxDistance, civilianSpawnPhase1MinDistance;
	private static @Getter double civilianSpawnRadiusShrinkStep, civilianSpawnSpawnerPreferenceRadius,
			civilianSpawnVisibilityCheckDistance;
	private static @Getter int civilianSpawnVerticalSearchRange, civilianSpawnYOffset,
			civilianSpawnMinOpenHorizontalSides;
	private static @Getter int civilianSpawnPhase1Attempts, civilianSpawnPhase2Attempts;
	// shared NPC navigation configuration
	private static @Getter int npcNavRecalculationTicks, npcNavStuckCheckInterval, npcNavMaxStuckChecks,
			npcNavMaxHopelessStuckChecks, npcNavMinRepathAfterLossTicks;
	private static @Getter double npcNavHopelessCloseThreshold, npcNavMinProgressDistance, npcNavRangedMinDistance,
			npcNavRangedMaxDistance;
	// cop core configuration
	private static @Getter int copMaxPerPlayer, copAiTickRate, copSpawnCheckRate, copMaxCuffAttempts,
			copCuffCooldownTicks, copAttackCooldownTicks;
	private static @Getter double copCuffRadius, copAlertRange, copCombatRange;
	// cop count configuration
	private static @Getter boolean copCountFormulaEnabled;
	private static @Getter String  copCountFormula;
	private static @Getter int     copCountBase, copCountPerLevel, copCountMax;
	// cop spawn configuration
	private static @Getter double copSpawnMinDistance, copSpawnMaxDistance, copSpawnPhase1MinDistance;
	private static @Getter double copSpawnRadiusShrinkStep, copSpawnSpawnerPreferenceRadius,
			copSpawnVisibilityCheckDistance;
	private static @Getter int copSpawnVerticalSearchRange, copSpawnYOffset, copSpawnMinOpenHorizontalSides;
	private static @Getter int copSpawnPhase1Attempts, copSpawnPhase2Attempts;
	// cop return / despawn configuration
	private static @Getter int    copReturnMaxTicks;
	private static @Getter double copReturnStationArrivalDistance;
	// cop misc configuration
	private static @Getter int    copStartingAmmoMagazines;
	private static @Getter int    jailMaxCapacity;
	// gadget - jetpack
	private static @Getter int    gadgetJetpackThrustRampTicks;
	private static @Getter double gadgetJetpackDescentAccel;
	private static @Getter double gadgetJetpackMaxDescentSpeed;
	private static @Getter double gadgetJetpackHorizInfluence;
	private static @Getter double gadgetJetpackMaxHorizSpeed;
	// gadget - car
	private static @Getter double gadgetCarReverseSpeedRatio;
	private static @Getter double gadgetCarHardBrakeMultiplier;
	private static @Getter int    gadgetCarFuelConsumePerTick;
	// block regeneration (weapon Break_Blocks modifier tuning)
	private static @Getter int    blockRestoreDelayTicks;
	private static @Getter int    blockRegenerationDelayTicks;
	private static @Getter int    blockRegenerationStepTicks;
	// trader configuration
	private static @Getter int    traderRespawnCooldownSeconds;
	private static @Getter int    traderHeadTrackRadius;
	private static @Getter String traderFallbackTraitId;
	private static @Getter int    traderMaxModeMultiplier;
	private static @Getter int    traderSellMaxOfferSlots;
	private static @Getter double traderMoodPerSale;
	private static @Getter double traderTipAmount;
	// loot chest configuration
	private static @Getter long   lootChestCountdownTimer;
	private static @Getter String lootChestOpeningSound, lootChestLockedSound, lootChestClosingSound;
	private static @Getter List<String> lootChestAllowedBlocks;
	private static @Getter double       lootChestRewardMoneyMinimum, lootChestRewardMoneyMaximum,
			lootChestRewardExperienceMinimum, lootChestRewardExperienceMaximum;
	private static @Getter List<String> lootChestRewardCommands;
	// money drop (cash items dropped by mobs / cops / civilians / players on death)
	private static @Getter boolean      moneyDropEnabled;
	private final          FileHandler  fileHandler;

	public Settings(FileManager fileManager) {
		try {
			String fileName = "settings";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));

			settings = fileHandler.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	public static String formatDouble(double value) {
		return NumberUtil.formatDouble(format(value), value);
	}

	public static Method getSetting(String methodName) {
		Method[] methods = Settings.class.getDeclaredMethods();

		for (Method method : methods) {
			if (!method.getName().endsWith(methodName)) continue;

			return method;
		}

		return null;
	}

	private static String format(double value) {
		if (settings.getBoolean("Balance_Format.Enable")) return String.format(balanceFormat, value);
		return String.valueOf(value);
	}

	/**
	 * Descend into {@code key} if it exists as a mapping. Returns {@code null} when absent — the scalar helpers below
	 * handle null readers by returning the default.
	 */
	private static NodeReader section(NodeReader parent, String key, ConfigReport report) {
		if (parent == null) return null;
		MappingNode child = parent.get(key).asMapping().orNull();
		if (child == null) return null;
		return NodeReader.of(child, report);
	}

	private static String str(NodeReader parent, String key, String def) {
		if (parent == null) return def;
		return parent.get(key).asString().orDefault(def);
	}

	private static int intVal(NodeReader parent, String key, int def) {
		if (parent == null) return def;
		return parent.get(key).asInt().orDefault(def);
	}

	private static double dbl(NodeReader parent, String key, double def) {
		if (parent == null) return def;
		return parent.get(key).asDouble().orDefault(def);
	}

	private static boolean bool(NodeReader parent, String key, boolean def) {
		if (parent == null) return def;
		return parent.get(key).asBool().orDefault(def);
	}

	// ── Section / scalar helpers ───────────────────────────────────────────────
	// Settings uses Bukkit's dotted-path convention heavily, but NodeReader only does literal key lookups.
	// These helpers walk a single step into a sub-mapping and return a reader pinned to it, producing a
	// wrapper that is safe to invoke with default fallbacks even when the section is absent.

	private static List<String> strList(NodeReader parent, String key) {
		if (parent == null) return Collections.emptyList();
		return parent.get(key).asList().ofStrings().orEmpty();
	}

	private static List<Integer> intList(NodeReader parent, String key) {
		if (parent == null) return Collections.emptyList();
		return parent.get(key).asList().ofInts().orEmpty();
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		init();
	}

	private void init() {
		ConfigReport report = new ConfigReport();
		NodeReader   root   = FileHandlerReader.read(fileHandler, report);

		// update configuration
		NodeReader updateChecker = section(root, "Update_Checker", report);
		updaterEnabled          = bool(updateChecker, "Enable", true);
		notifyPrivilegedPlayers = bool(updateChecker, "Notify_Privileged_Players", false);
		updaterAutoUpdate       = bool(updateChecker, "Auto_Update", true);

		// language picked
		languagePicked = str(root, "Language", "en");

		// resource pack
		NodeReader resourcePack = section(root, "Resource_Pack", report);
		resourcePackEnabled = bool(resourcePack, "Enable", true);
		resourcePackUrl     = str(resourcePack, "URL", "");
		resourcePackKick    = bool(resourcePack, "Kick", false);

		// database
		NodeReader database        = section(root, "Database", report);
		NodeReader mysql           = section(database, "MySQL", report);
		NodeReader sqlite          = section(database, "SQLite", report);
		NodeReader autoSaveSection = section(database, "Auto_Save", report);
		NodeReader cleanUp         = section(database, "Clean_Up", report);

		databaseType      = str(database, "Type", "sqlite");
		mysqlHost         = str(mysql, "Host", "localhost");
		mysqlUsername     = str(mysql, "Username", "root");
		mysqlPassword     = str(mysql, "Password", "");
		mysqlPort         = intVal(mysql, "Port", 3306);
		sqliteBackup      = bool(sqlite, "Backup", true);
		sqliteFailedMysql = bool(sqlite, "Failed_MySQL", true);
		autoSave          = bool(autoSaveSection, "Enable", true);
		autoSaveDebug     = bool(autoSaveSection, "Debug", true);
		autoSaveTime      = intVal(autoSaveSection, "Time", 10);
		cleanUpTime       = dbl(cleanUp, "Time", 30);

		// inventory
		NodeReader inventory      = section(root, "Inventory", report);
		NodeReader inventoryFill  = section(inventory, "Fill", report);
		NodeReader inventoryLine  = section(inventory, "Line", report);
		NodeReader multiInventory = section(inventory, "Multi_Inventory", report);

		inventoryFillItem = str(inventoryFill, "Item", "BLACK_STAINED_GLASS_PANE");
		inventoryFillName = str(inventoryFill, "Name", " ");
		inventoryLineItem = str(inventoryLine, "Item", "WHITE_STAINED_GLASS_PANE");
		inventoryLineName = str(inventoryLine, "Name", " ");

		nextPage     = str(multiInventory, "Next_Page",
		                   "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYzMzlmZjJlNTM0MmJhMThiZGM0OGE5OWNjYTY1ZDEyM2NlNzgxZDg3ODI3MmY5ZDk2NGVhZDNiOGFkMzcwIn19fQ==");
		previousPage = str(multiInventory, "Previous_Page",
		                   "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg0ZjU5NzEzMWJiZTI1ZGMwNThhZjg4OGNiMjk4MzFmNzk1OTliYzY3Yzk1YzgwMjkyNWNlNGFmYmEzMzJmYyJ9fX0=");
		homePage     = str(multiInventory, "Home_Page",
		                   "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg0ZjU5NzEzMWJiZTI1ZGMwNThhZjg4OGNiMjk4MzFmNzk1OTliYzY3Yzk1YzgwMjkyNWNlNGFmYmEzMzJmYyJ9fX0=");

		// economy
		moneySymbol = str(root, "Money_Symbol", "$").substring(0, 1);
		NodeReader balanceFormatSection = section(root, "Balance_Format", report);
		balanceFormat = str(balanceFormatSection, "Format", "%,.2f");

		// user
		NodeReader user    = section(root, "User", report);
		NodeReader account = section(user, "Account", report);
		NodeReader bank    = section(user, "Bank", report);
		NodeReader level   = section(user, "Level", report);
		NodeReader skill   = section(level, "Skill", report);

		userInitialBalance   = dbl(account, "Initial_Balance", 0);
		userMaxBalance       = dbl(account, "Maximum_Balance", 10_000_000);
		bankInitialBalance   = dbl(bank, "Initial_Balance", 0);
		bankCreateFee        = dbl(bank, "Create_Cost", 5_000);
		bankMaxBalance       = dbl(bank, "Maximum_Balance", 1_000_000_000);
		userMaxLevel         = intVal(level, "Maximum_Level", 100);
		userLevelBaseAmount  = intVal(level, "Base_Amount", 1_000);
		userLevelFormula     = str(level, "Formula", "base * level ^ 1.5");
		userSkillUpgrade     = intVal(skill, "Upgrade", 1);
		userSkillCost        = dbl(skill, "Cost", 500);
		userSkillExponential = dbl(skill, "Exponential", 1.8);

		// user death
		NodeReader death      = section(user, "Death", report);
		NodeReader deathMoney = section(death, "Money", report);
		NodeReader deathCmd   = section(deathMoney, "Command", report);

		deathEnabled                 = bool(death, "Enable", true);
		deathMoneyCommandEnabled     = bool(deathCmd, "Enable", false);
		deathMoneyCommandExecutables = strList(deathCmd, "Executable");
		deathLoseMoney               = bool(deathMoney, "Lose_Money", true);
		deathLoseMoneyFormula        = str(deathMoney, "Formula", "balance * 0.15");
		deathThreshold               = dbl(deathMoney, "Threshold", 1_000);

		// respawn
		NodeReader respawn  = section(death, "Respawn", report);
		NodeReader screen   = section(respawn, "Screen", report);
		NodeReader gameMode = section(respawn, "GameMode", report);
		NodeReader teleport = section(respawn, "Teleport", report);

		respawnEnabled          = bool(respawn, "Enable", false);
		respawnDelay            = intVal(respawn, "Delay", 10);
		respawnScreenEnabled    = bool(screen, "Enable", true);
		respawnScreenTitle      = str(screen, "Title", "&cWASTED");
		respawnScreenSubtitle   = str(screen, "Subtitle", "&7Respawning after &a%time% &7seconds");
		respawnGameMode         = str(gameMode, "Change_To", "spectator");
		respawnGameModeAllowFly = bool(gameMode, "Allow_Fly", true);
		respawnHealthAmount     = dbl(respawn, "Health", 20);
		respawnHungerAmount     = intVal(respawn, "Hunger", 20);
		respawnTeleportEnabled  = bool(teleport, "Enable", false);
		respawnTeleportWaypoint = str(teleport, "Waypoint", "spawn");

		// bounty
		NodeReader bounty      = section(root, "Bounty", report);
		NodeReader bountyKill  = section(bounty, "Kill", report);
		NodeReader bountyTimer = section(bounty, "Repeating_Timer", report);

		bountyEachKillValue = dbl(bountyKill, "Each", 5);
		bountyMaxKill       = dbl(bountyKill, "Maximum", 50_000);
		bountyTimerEnabled  = bool(bountyTimer, "Enable", true);
		bountyTimerMultiple = dbl(bountyTimer, "Multiple", 2);
		bountyTimeInterval  = intVal(bountyTimer, "Time", 300);
		bountyTimerMax      = dbl(bountyTimer, "Maximum", 20_000);

		// wanted
		NodeReader wanted             = section(root, "Wanted", report);
		NodeReader wantedTakeMoney    = section(wanted, "Take_Money", report);
		NodeReader wantedTimer        = section(wanted, "Repeating_Timer", report);
		NodeReader wantedMultiplier   = section(wantedTimer, "Multiplier", report);
		NodeReader wantedLevel        = section(wanted, "Level", report);
		NodeReader wantedKillComboSec = section(wanted, "Kill_Combo", report);

		wantedEnabled                = bool(wanted, "Enable", true);
		wantedTakeMoneyAmount        = dbl(wantedTakeMoney, "Amount", 50);
		wantedTakeMoneyMultiplier    = dbl(wantedTakeMoney, "Multiplier", 5);
		wantedTimerEnabled           = bool(wantedTimer, "Enable", true);
		wantedTimerTime              = intVal(wantedTimer, "Time", 120);
		wantedTimerMultiplierEnabled = bool(wantedMultiplier, "Enable", true);
		wantedTimerMultiplierAmount  = dbl(wantedMultiplier, "Amount", 1.1);
		wantedLevelIncrement         = intVal(wantedLevel, "Increment", 1);
		wantedMaximumLevel           = intVal(wantedLevel, "Maximum", 5);
		wantedKillComboEnabled       = bool(wantedKillComboSec, "Enable", true);
		wantedKillComboResetAfter    = intVal(wantedKillComboSec, "Reset_After", 10);
		wantedKillCounter            = intList(wantedKillComboSec, "Kill_Counter");

		// gang
		NodeReader gang        = section(root, "Gang", report);
		NodeReader gangRank    = section(gang, "Rank", report);
		NodeReader gangAccount = section(gang, "Account", report);

		gangEnabled          = bool(gang, "Enable", true);
		gangNameDuplicates   = bool(gang, "Name_Duplicates", false);
		gangRankHead         = str(gangRank, "Head", "member");
		gangRankTail         = str(gangRank, "Tail", "owner");
		gangDisplayNameChar  = str(gang, "Display_Name_Char", "*").substring(0, 1);
		gangInitialBalance   = dbl(gangAccount, "Initial_Balance", 0);
		gangCreateFee        = dbl(gangAccount, "Create_Cost", 100_000);
		gangMaxBalance       = dbl(gangAccount, "Maximum_Balance", 100_000_000_000.0);
		gangContributionRate = dbl(gangAccount, "Contribution_Rate", 1_000);

		// scoreboard
		NodeReader scoreboard = section(root, "Scoreboard", report);
		scoreboardEnabled = bool(scoreboard, "Enable", true);
		scoreboardDriver  = str(scoreboard, "Driver", "Driver_V3");

		// civilian
		NodeReader civilian                 = section(root, "Civilians", report);
		NodeReader civilianBehaviour        = section(civilian, "Behaviour", report);
		NodeReader civilianSpawnerProximity = section(civilian, "Spawner_Proximity", report);
		NodeReader civiliansSpawn           = section(civilian, "Spawn", report);

		civilianAiEnabled  = bool(civilianBehaviour, "Enabled", true);
		civilianAiTickRate = intVal(civilianBehaviour, "AI_Tick_Rate", 20);

		civilianSpawnerActivationRadius = dbl(civilianSpawnerProximity, "Activation_Radius", 60.0);
		civilianSpawnerDespawnRadius    = dbl(civilianSpawnerProximity, "Despawn_Radius", 80.0);
		civilianSpawnerMaxNpcs          = intVal(civilianSpawnerProximity, "Max_Npcs_Per_Spawner", 5);
		civilianSpawnerCheckInterval    = intVal(civilianSpawnerProximity, "Check_Interval", 100);
		civilianSpawnerDefaultTypeId    = str(civilianSpawnerProximity, "Default_Type_Id", "");

		civilianSpawnMinDistance             = dbl(civiliansSpawn, "Min_Distance", 10.0);
		civilianSpawnMaxDistance             = dbl(civiliansSpawn, "Max_Distance", 50.0);
		civilianSpawnPhase1MinDistance       = dbl(civiliansSpawn, "Phase1_Min_Distance", 30.0);
		civilianSpawnRadiusShrinkStep        = dbl(civiliansSpawn, "Radius_Shrink_Step", 5.0);
		civilianSpawnVerticalSearchRange     = intVal(civiliansSpawn, "Vertical_Search_Range", 10);
		civilianSpawnYOffset                 = intVal(civiliansSpawn, "Y_Offset", 0);
		civilianSpawnMinOpenHorizontalSides  = intVal(civiliansSpawn, "Min_Open_Sides", 2);
		civilianSpawnSpawnerPreferenceRadius = dbl(civiliansSpawn, "Spawner_Preference_Radius", 80.0);
		civilianSpawnVisibilityCheckDistance = dbl(civiliansSpawn, "Visibility_Check_Distance", 48.0);
		civilianSpawnPhase1Attempts          = intVal(civiliansSpawn, "Phase1_Attempts", 20);
		civilianSpawnPhase2Attempts          = intVal(civiliansSpawn, "Phase2_Attempts", 15);

		// shared NPC navigation
		NodeReader npcNav = section(root, "NPC_Navigation", report);
		npcNavRecalculationTicks      = intVal(npcNav, "Recalculation_Ticks", 10);
		npcNavStuckCheckInterval      = intVal(npcNav, "Stuck_Check_Interval", 5);
		npcNavMaxStuckChecks          = intVal(npcNav, "Max_Stuck_Checks", 3);
		npcNavMaxHopelessStuckChecks  = intVal(npcNav, "Max_Hopeless_Stuck_Checks", 6);
		npcNavMinRepathAfterLossTicks = intVal(npcNav, "Min_Repath_After_Loss_Ticks", 2);
		npcNavHopelessCloseThreshold  = dbl(npcNav, "Hopeless_Close_Threshold", 8.0);
		npcNavMinProgressDistance     = dbl(npcNav, "Min_Progress_Distance", 0.75);
		npcNavRangedMinDistance       = dbl(npcNav, "Ranged_Min_Distance", 7.0);
		npcNavRangedMaxDistance       = dbl(npcNav, "Ranged_Max_Distance", 12.0);

		// cop core
		NodeReader cop         = section(root, "Cops", report);
		NodeReader copBehavior = section(cop, "Behaviour", report);
		NodeReader copsCount   = section(cop, "Count", report);
		NodeReader copsSpawn   = section(cop, "Spawn", report);
		NodeReader copsReturn  = section(cop, "Return", report);

		copMaxPerPlayer        = intVal(copBehavior, "Max_Per_Player", 8);
		copAiTickRate          = intVal(copBehavior, "AI_Tick_Rate", 10);
		copSpawnCheckRate      = intVal(copBehavior, "Spawn_Check_Rate", 40);
		copCuffRadius          = dbl(copBehavior, "Cuff_Radius", 3.0);
		copMaxCuffAttempts     = intVal(copBehavior, "Max_Cuff_Attempts", 3);
		copCuffCooldownTicks   = intVal(copBehavior, "Cuff_Cooldown_Ticks", 100);
		copAlertRange          = dbl(copBehavior, "Alert_Range", 40.0);
		copCombatRange         = dbl(copBehavior, "Combat_Range", 4.0);
		copAttackCooldownTicks = intVal(copBehavior, "Attack_Cooldown_Ticks", 20);

		copCountFormulaEnabled = bool(copsCount, "Formula_Enabled", false);
		copCountFormula        = str(copsCount, "Formula", "base + (level - 1) * perLevel");
		copCountBase           = intVal(copsCount, "Base", 2);
		copCountPerLevel       = intVal(copsCount, "Per_Level", 1);
		copCountMax            = intVal(copsCount, "Max", 8);

		copSpawnMinDistance             = dbl(copsSpawn, "Min_Distance", 10.0);
		copSpawnMaxDistance             = dbl(copsSpawn, "Max_Distance", 50.0);
		copSpawnPhase1MinDistance       = dbl(copsSpawn, "Phase1_Min_Distance", 30.0);
		copSpawnRadiusShrinkStep        = dbl(copsSpawn, "Radius_Shrink_Step", 5.0);
		copSpawnVerticalSearchRange     = intVal(copsSpawn, "Vertical_Search_Range", 10);
		copSpawnYOffset                 = intVal(copsSpawn, "Y_Offset", 0);
		copSpawnMinOpenHorizontalSides  = intVal(copsSpawn, "Min_Open_Sides", 2);
		copSpawnSpawnerPreferenceRadius = dbl(copsSpawn, "Spawner_Preference_Radius", 80.0);
		copSpawnVisibilityCheckDistance = dbl(copsSpawn, "Visibility_Check_Distance", 48.0);
		copSpawnPhase1Attempts          = intVal(copsSpawn, "Phase1_Attempts", 20);
		copSpawnPhase2Attempts          = intVal(copsSpawn, "Phase2_Attempts", 15);

		copReturnMaxTicks               = intVal(copsReturn, "Max_Ticks", 600);
		copReturnStationArrivalDistance = dbl(copsReturn, "Station_Arrival_Distance", 3.0);

		copStartingAmmoMagazines = intVal(cop, "Starting_Ammo_Magazines", 3);

		// detainment
		NodeReader detainment = section(root, "Detainment", report);
		NodeReader jail       = section(detainment, "Jail", report);
		jailMaxCapacity = intVal(jail, "Max_Capacity", 10);

		// loot chest
		NodeReader lootChest        = section(root, "Loot_Chest", report);
		NodeReader lootChestSound   = section(lootChest, "Sound", report);
		NodeReader lootChestRewards = section(lootChest, "Rewards", report);
		NodeReader lootRewardMoney  = section(lootChestRewards, "Money", report);
		NodeReader lootRewardExp    = section(lootChestRewards, "Experience", report);

		lootChestCountdownTimer = intVal(lootChest, "Countdown_Timer", 300);
		lootChestOpeningSound   = str(lootChestSound, "Opening", "BLOCK_CHEST_OPEN");
		lootChestLockedSound    = str(lootChestSound, "Locked", "BLOCK_CHEST_LOCKED");
		lootChestClosingSound   = str(lootChestSound, "Closing", "BLOCK_CHEST_CLOSE");
		lootChestAllowedBlocks  = strList(lootChest, "Allowed_Blocks");

		lootChestRewardMoneyMinimum      = dbl(lootRewardMoney, "Minimum", 10);
		lootChestRewardMoneyMaximum      = dbl(lootRewardMoney, "Maximum", 1_000);
		lootChestRewardExperienceMinimum = dbl(lootRewardExp, "Minimum", 5);
		lootChestRewardExperienceMaximum = dbl(lootRewardExp, "Maximum", 100);
		lootChestRewardCommands          = strList(lootChestRewards, "Commands");

		// money drop — optional section; defaults true when missing
		MappingNode moneyDropNode = root.get("Money_Drop").asMapping().orNull();
		moneyDropEnabled = moneyDropNode == null ||
		                   NodeReader.of(moneyDropNode, report).get("Enabled").asBool().orDefault(true);

		// gadgets
		NodeReader gadgets       = section(root, "Gadgets", report);
		NodeReader gadgetJetpack = section(gadgets, "Jetpack", report);
		NodeReader gadgetCar     = section(gadgets, "Car", report);

		gadgetJetpackThrustRampTicks = intVal(gadgetJetpack, "Thrust_Ramp_Ticks", 20);
		gadgetJetpackDescentAccel    = dbl(gadgetJetpack, "Descent_Accel", 0.022);
		gadgetJetpackMaxDescentSpeed = dbl(gadgetJetpack, "Max_Descent_Speed", -0.5);
		gadgetJetpackHorizInfluence  = dbl(gadgetJetpack, "Horiz_Influence", 0.03);
		gadgetJetpackMaxHorizSpeed   = dbl(gadgetJetpack, "Max_Horiz_Speed", 0.25);

		gadgetCarReverseSpeedRatio   = dbl(gadgetCar, "Reverse_Speed_Ratio", 0.5);
		gadgetCarHardBrakeMultiplier = dbl(gadgetCar, "Hard_Brake_Multiplier", 3.0);
		gadgetCarFuelConsumePerTick  = intVal(gadgetCar, "Fuel_Consume_Per_Tick", 1);

		// block regeneration
		NodeReader blockRegeneration = section(root, "Block_Regeneration", report);
		blockRestoreDelayTicks      = intVal(blockRegeneration, "Restore_Delay_Ticks", 100);
		blockRegenerationDelayTicks = intVal(blockRegeneration, "Regeneration_Delay_Ticks", 100);
		blockRegenerationStepTicks  = intVal(blockRegeneration, "Regeneration_Step_Ticks", 4);

		// trader
		NodeReader trader     = section(root, "Trader", report);
		NodeReader traderSell = section(trader, "Sell", report);

		traderRespawnCooldownSeconds = intVal(trader, "Respawn_Cooldown", 60);
		traderHeadTrackRadius        = intVal(trader, "Head_Track_Radius", 8);
		traderFallbackTraitId        = str(trader, "Fallback_Trait_Id", "easygoing");
		traderMaxModeMultiplier      = intVal(trader, "Max_Mode_Multiplier", 1_000_000);
		traderSellMaxOfferSlots      = intVal(traderSell, "Max_Offer_Slots", 20);
		traderMoodPerSale            = dbl(traderSell, "Mood_Per_Sale", 0.02);
		traderTipAmount              = dbl(trader, "Tip_Amount", 100.0);

		if (!report.isEmpty()) report.log(log);

		addEachFieldReflection();
		convertToPlaceholder();
	}

	private void addEachFieldReflection() {
		Field[] fields = this.getClass().getDeclaredFields();

		for (Field field : fields) {
			// only static fields are exposed via the placeholder map; skip instance fields like fileHandler
			if (!Modifier.isStatic(field.getModifiers())) continue;

			field.setAccessible(true);
			try {
				Object value = field.get(null);

				settingsMap.put(field.getName(), value);
			} catch (IllegalAccessException exception) {
				log.error(exception);
			}
		}

		// need to remove the map to show the other values separately
		settingsMap.remove("settingsMap");
		settingsMap.remove("settingsPlaceholder");
		settingsMap.remove("settings");
	}

	private void convertToPlaceholder() {
		for (Map.Entry<String, Object> entry : settingsMap.entrySet()) {
			String key     = entry.getKey();
			String replace = key.replaceAll("(?<=[a-z])(?=[A-Z])", "_");
			String lower   = replace.toLowerCase();

			Object value = entry.getValue();

			settingsPlaceholder.put(lower, value);
		}
	}

}
