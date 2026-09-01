package org.luckyraven.gangland.file.configuration;

import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;

@CustomLog
public class Settings implements FileInitializer {

	private static final @Getter Map<String, Object> settingsMap         = new LinkedHashMap<>();
	private static final @Getter Map<String, Object> settingsPlaceholder = new LinkedHashMap<>();

	// debug configuration
	private static @Getter boolean      debugEnabled;
	private static @Getter List<String> debugModules;
	// update configuration
	private static @Getter boolean      updaterEnabled, notifyPrivilegedPlayers, updaterAutoUpdate;
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
	private static @Getter boolean    balanceFormatEnabled;
	// user configuration
	private static @Getter BigDecimal userInitialBalance;
	private static @Getter BigDecimal userMaxBalance;
	private static @Getter BigDecimal bankInitialBalance;
	private static @Getter BigDecimal bankCreateFee;
	private static @Getter BigDecimal bankRenameFee;
	private static @Getter long       bankResetPeriodSeconds;
	// user levels
	private static @Getter int        userMaxLevel, userLevelBaseAmount;
	private static @Getter String  userLevelFormula;
	private static @Getter int     userSkillUpgrade;
	private static @Getter double  userSkillCost;
	private static @Getter String  userSkillFormula;
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
	private static @Getter boolean    respawnGameModeAllowFly;
	private static @Getter String     respawnGameMode;
	private static @Getter double     respawnHealthAmount;
	private static @Getter int        respawnHungerAmount;
	private static @Getter boolean    respawnTeleportEnabled;
	private static @Getter String     respawnTeleportWaypoint;
	// bounty configuration
	private static @Getter BigDecimal bountyEachKillValue;
	private static @Getter BigDecimal bountyMaxKill;
	private static @Getter boolean    bountyTimerEnabled;
	private static @Getter double     bountyTimerMultiple, bountyTimerMax;
	private static @Getter int        bountyTimeInterval;
	// wanted configuration
	private static @Getter BigDecimal wantedTakeMoneyAmount;
	private static @Getter double     wantedTakeMoneyMultiplier, wantedTimerMultiplierAmount;
	private static @Getter boolean wantedEnabled, wantedTimerEnabled, wantedTimerMultiplierEnabled,
			wantedKillComboEnabled;
	private static @Getter int wantedTimerTime, wantedLevelIncrement, wantedMaximumLevel, wantedKillComboResetAfter;
	private static @Getter List<Integer> wantedKillCounter;
	// gang configuration
	private static @Getter boolean       gangEnabled, gangNameDuplicates;
	private static @Getter String gangRankHead, gangRankTail, gangDisplayNameChar;
	private static @Getter BigDecimal gangInitialBalance;
	private static @Getter BigDecimal gangCreateFee;
	private static @Getter BigDecimal gangMaxBalance;
	private static @Getter double     gangContributionRate;
	// scoreboard configuration
	private static @Getter boolean    scoreboardEnabled;
	private static @Getter String     scoreboardDriver;
	// civilian AI configuration
	private static @Getter boolean    civilianAiEnabled;
	private static @Getter int        civilianAiTickRate;
	// civilian spawner proximity configuration
	private static @Getter double     civilianSpawnerActivationRadius;
	private static @Getter double     civilianSpawnerDespawnRadius;
	private static @Getter int        civilianSpawnerMaxNpcs;
	private static @Getter double     civilianSpawnerSoftLeashRadius;
	private static @Getter double     civilianSpawnerHardLeashRadius;
	private static @Getter int        civilianSpawnerCheckInterval;
	private static @Getter String     civilianSpawnerDefaultTypeId;
	// civilian spawn configuration
	private static @Getter double     civilianSpawnMinDistance, civilianSpawnMaxDistance,
			civilianSpawnPhase1MinDistance;
	private static @Getter double civilianSpawnRadiusShrinkStep, civilianSpawnSpawnerPreferenceRadius,
			civilianSpawnVisibilityCheckDistance;
	private static @Getter int civilianSpawnVerticalSearchRange, civilianSpawnYOffset,
			civilianSpawnMinOpenHorizontalSides;
	private static @Getter int civilianSpawnPhase1Attempts, civilianSpawnPhase2Attempts;
	private static @Getter double civilianSpawnMaxYDiff, civilianSpawnSpawnerMaxYDiff;
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
	private static @Getter double copSpawnMaxYDiff, copSpawnSpawnerMaxYDiff;
	// cop pursuit leash configuration
	private static @Getter double     copPursuitMaxDistance;
	private static @Getter int        copPursuitMaxTicks;
	// cop return / despawn configuration
	private static @Getter int        copReturnMaxTicks;
	private static @Getter double     copReturnStationArrivalDistance;
	// cop misc configuration
	private static @Getter int        copStartingAmmoMagazines;
	private static @Getter int        jailMaxCapacity;
	// detainment transit / guard
	private static @Getter int        detainmentTransitDelayTicks;
	private static @Getter double     detainmentGuardRadius;
	// detainment break-free minigame
	private static @Getter int        detainmentBreakFreeTapsRequired;
	private static @Getter int        detainmentBreakFreeResetWindowTicks;
	// detainment handcuff bribe
	private static @Getter double     detainmentHandcuffBribeBaseCost;
	private static @Getter double     detainmentHandcuffBribePerLevel;
	// detainment bail
	private static @Getter double     detainmentBailBaseCost;
	private static @Getter double     detainmentBailPerLevel;
	// detainment jail bribe
	private static @Getter double     detainmentJailBribeBaseCost;
	private static @Getter double     detainmentJailBribePerLevel;
	private static @Getter double     detainmentJailBribeSuccessChance;
	private static @Getter int        detainmentJailBribeFailPenaltySeconds;
	// detainment sentence
	private static @Getter int        detainmentSentenceBaseSeconds;
	private static @Getter int        detainmentSentencePerWantedLevelSeconds;
	// detainment fallback exit waypoint
	private static @Getter String     detainmentFallbackExitWaypoint;
	// detainment sounds (XSound names; built into SoundConfiguration at playback)
	private static @Getter String     detainmentBailSuccessSound;
	private static @Getter String     detainmentBribeSuccessSound;
	private static @Getter String     detainmentBribeFailSound;
	private static @Getter String     detainmentTransitCommitSound;
	private static @Getter String     detainmentSentenceCompleteSound;
	// gadget - jetpack
	private static @Getter int        gadgetJetpackThrustRampTicks;
	private static @Getter double     gadgetJetpackDescentAccel;
	private static @Getter double     gadgetJetpackMaxDescentSpeed;
	private static @Getter double     gadgetJetpackHorizInfluence;
	private static @Getter double     gadgetJetpackMaxHorizSpeed;
	// gadget - car
	private static @Getter double     gadgetCarReverseSpeedRatio;
	private static @Getter double     gadgetCarHardBrakeMultiplier;
	private static @Getter int        gadgetCarFuelConsumePerTick;
	// block regeneration (weapon Break_Blocks modifier tuning)
	private static @Getter int        blockRestoreDelayTicks;
	private static @Getter int        blockRegenerationDelayTicks;
	private static @Getter int        blockRegenerationStepTicks;
	// trader configuration
	private static @Getter int        traderRespawnCooldownSeconds;
	private static @Getter int        traderHeadTrackRadius;
	private static @Getter String     traderFallbackTraitId;
	private static @Getter int        traderMaxModeMultiplier;
	private static @Getter int        traderSellMaxOfferSlots;
	private static @Getter double     traderMoodPerSale;
	private static @Getter BigDecimal traderTipAmount;
	// banker configuration
	private static @Getter int        bankerHeadTrackRadius;
	private static @Getter double     bankerMaxHealth;
	private static @Getter boolean    bankerInvulnerable;
	private static @Getter String     bankerFallbackTierId;
	// loot chest configuration
	private static @Getter long       lootChestCountdownTimer;
	private static @Getter String     lootChestOpeningSound, lootChestLockedSound, lootChestClosingSound;
	private static @Getter List<String> lootChestAllowedBlocks;
	private static @Getter double       lootChestRewardMoneyMinimum, lootChestRewardMoneyMaximum,
			lootChestRewardExperienceMinimum, lootChestRewardExperienceMaximum;
	private static @Getter List<String>  lootChestRewardCommands;
	// money drop (cash items dropped by mobs / cops / civilians / players on death)
	private static @Getter boolean       moneyDropEnabled;
	// turf configuration
	private static @Getter int           turfIncomeIntervalMinutes;
	private static @Getter BigDecimal    turfDefaultIncomeAmount;
	private static @Getter String        turfWandItemType;
	private static @Getter int           turfVisualizationDurationSeconds;
	private static @Getter String        turfVisualizationParticle;
	private static @Getter boolean       turfShowEnterTitle;
	// turf - capture
	private static @Getter int           turfCaptureDurationSeconds;
	private static @Getter int           turfCaptureUnclaimedPhase1Seconds;
	private static @Getter int           turfCaptureUnclaimedPhase2Seconds;
	private static @Getter int           turfCaptureCooldownMinutes;
	private static @Getter int           turfCaptureAbandonGraceSeconds;
	private static @Getter int           turfCapturePostLogoffProtectionMinutes;
	private static @Getter int           turfCaptureInactivityAutoReleaseDays;
	private static @Getter boolean       turfCaptureSoundEnabled;
	private static @Getter boolean       turfCaptureBroadcastGlobally;
	private static @Getter List<Integer> turfCaptureProgressMilestones;
	// turf - sounds
	private static @Getter String        turfCaptureSoundStartName, turfCaptureSoundCompleteName,
			turfCaptureSoundFailedName, turfCaptureSoundTickName, turfCaptureSoundUnclaimedName;
	private static @Getter double turfCaptureSoundStartVolume, turfCaptureSoundCompleteVolume,
			turfCaptureSoundFailedVolume, turfCaptureSoundTickVolume, turfCaptureSoundUnclaimedVolume;
	private static @Getter double turfCaptureSoundStartPitch, turfCaptureSoundCompletePitch,
			turfCaptureSoundFailedPitch, turfCaptureSoundTickPitch, turfCaptureSoundUnclaimedPitch;
	// turf - contribution
	private static @Getter double      turfContributionDefenderPresenceTick;
	private static @Getter double      turfContributionAttackerPresenceTick;
	private static @Getter double      turfContributionCaptureCompleteBonus;
	private static @Getter double      turfContributionDefenseSuccessBonus;
	private final          FileHandler fileHandler;

	public Settings(FileManager fileManager) {
		try {
			String fileName = "settings";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	public static String formatDouble(double value) {
		return NumberUtil.formatDouble(format(value), value);
	}

	/**
	 * BigDecimal-aware currency formatter. Delegates to {@link #formatDouble(double)} via {@code doubleValue()} so
	 * existing formatter config (thousand separators, decimal places) applies unchanged; values beyond {@code 2^53}
	 * lose precision in the rendered string but still round-trip through the DB via
	 * {@link Currency#plainString(BigDecimal)}.
	 */
	public static String formatAmount(BigDecimal value) {
		return NumberUtil.valueFormat(value);
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
		if (balanceFormatEnabled) return String.format(balanceFormat, value);
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

	/**
	 * Currency-typed parser. Reads the YAML value as a string so literals bigger than {@code 2^53} round-trip without
	 * {@code double} precision loss. Falls back to {@code def} (parsed as a plain-decimal string) if the key is missing
	 * or malformed.
	 */
	private static BigDecimal money(NodeReader parent, String key, String def) {
		if (parent == null) return Currency.parse(def);
		String raw = parent.get(key).asString().orDefault(def);
		try {
			return Currency.parse(raw);
		} catch (NumberFormatException e) {
			return Currency.parse(def);
		}
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

		// debug configuration
		NodeReader debug = section(root, "Debug", report);
		debugEnabled = bool(debug, "Enabled", false);
		debugModules = strList(debug, "Modules");

		// update configuration
		NodeReader updateChecker = section(root, "Update_Checker", report);
		updaterEnabled          = bool(updateChecker, "Enable", true);
		notifyPrivilegedPlayers = bool(updateChecker, "Notify_Privileged_Players", false);
		updaterAutoUpdate       = bool(updateChecker, "Auto_Download", true);

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
		balanceFormatEnabled = bool(balanceFormatSection, "Enable", true);
		balanceFormat        = str(balanceFormatSection, "Format", "%,.2f");

		// user
		NodeReader user    = section(root, "User", report);
		NodeReader account = section(user, "Account", report);
		NodeReader bank    = section(user, "Bank", report);
		NodeReader level   = section(user, "Level", report);
		NodeReader skill   = section(level, "Skill", report);

		userInitialBalance     = money(account, "Initial_Balance", "0");
		userMaxBalance         = money(account, "Maximum_Balance", "10000000");
		bankInitialBalance     = money(bank, "Initial_Balance", "0");
		bankCreateFee          = money(bank, "Create_Cost", "5000");
		bankRenameFee          = money(bank, "Rename_Fee", "1000");
		bankResetPeriodSeconds = intVal(bank, "Reset_Period_Seconds", 86_400);
		userMaxLevel           = intVal(level, "Maximum_Level", 100);
		userLevelBaseAmount    = intVal(level, "Base_Amount", 1_000);
		userLevelFormula       = str(level, "Formula", "base * level ^ 1.5");
		userSkillUpgrade       = intVal(skill, "Upgrade", 1);
		userSkillCost          = dbl(skill, "Cost", 500);
		userSkillFormula       = str(skill, "Formula", "base * level ^ 1.8");

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

		bountyEachKillValue = money(bountyKill, "Each", "5");
		bountyMaxKill       = money(bountyKill, "Maximum", "50000");
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
		wantedTakeMoneyAmount        = money(wantedTakeMoney, "Amount", "50");
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
		gangInitialBalance   = money(gangAccount, "Initial_Balance", "0");
		gangCreateFee        = money(gangAccount, "Create_Cost", "100000");
		gangMaxBalance       = money(gangAccount, "Maximum_Balance", "100000000000");
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
		civilianSpawnerSoftLeashRadius  = dbl(civilianSpawnerProximity, "Npc_Soft_Leash_Radius", 30.0);
		civilianSpawnerHardLeashRadius  = dbl(civilianSpawnerProximity, "Npc_Hard_Leash_Radius", 50.0);
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
		civilianSpawnMaxYDiff                = dbl(civiliansSpawn, "Max_Y_Diff", 4.0);
		civilianSpawnSpawnerMaxYDiff         = dbl(civiliansSpawn, "Spawner_Max_Y_Diff", 16.0);

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
		NodeReader copsPursuit = section(cop, "Pursuit", report);
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
		copSpawnMaxYDiff                = dbl(copsSpawn, "Max_Y_Diff", 4.0);
		copSpawnSpawnerMaxYDiff         = dbl(copsSpawn, "Spawner_Max_Y_Diff", 16.0);

		copPursuitMaxDistance = dbl(copsPursuit, "Max_Distance", 80.0);
		copPursuitMaxTicks    = intVal(copsPursuit, "Max_Ticks", 120);

		copReturnMaxTicks               = intVal(copsReturn, "Max_Ticks", 600);
		copReturnStationArrivalDistance = dbl(copsReturn, "Station_Arrival_Distance", 3.0);

		copStartingAmmoMagazines = intVal(cop, "Starting_Ammo_Magazines", 3);

		// detainment
		NodeReader detainment    = section(root, "Detainment", report);
		NodeReader jail          = section(detainment, "Jail", report);
		NodeReader transit       = section(detainment, "Transit", report);
		NodeReader breakFree     = section(detainment, "Break_Free", report);
		NodeReader handcuffBribe = section(detainment, "Handcuff_Bribe", report);
		NodeReader detainBail    = section(detainment, "Bail", report);
		NodeReader jailBribe     = section(detainment, "Jail_Bribe", report);
		NodeReader sentence      = section(detainment, "Sentence", report);
		NodeReader detainSounds  = section(detainment, "Sounds", report);

		jailMaxCapacity = intVal(jail, "Max_Capacity", 10);

		detainmentTransitDelayTicks = intVal(transit, "Delay_Ticks", 400);
		detainmentGuardRadius       = dbl(transit, "Guard_Radius", 5.0);

		detainmentBreakFreeTapsRequired     = intVal(breakFree, "Taps_Required", 25);
		detainmentBreakFreeResetWindowTicks = intVal(breakFree, "Reset_Window_Ticks", 40);

		detainmentHandcuffBribeBaseCost = dbl(handcuffBribe, "Base_Cost", 500.0);
		detainmentHandcuffBribePerLevel = dbl(handcuffBribe, "Per_Wanted_Level", 250.0);

		detainmentBailBaseCost = dbl(detainBail, "Base_Cost", 2_500.0);
		detainmentBailPerLevel = dbl(detainBail, "Per_Wanted_Level", 1_000.0);

		detainmentJailBribeBaseCost           = dbl(jailBribe, "Base_Cost", 1_000.0);
		detainmentJailBribePerLevel           = dbl(jailBribe, "Per_Wanted_Level", 500.0);
		detainmentJailBribeSuccessChance      = dbl(jailBribe, "Success_Chance", 0.35);
		detainmentJailBribeFailPenaltySeconds = intVal(jailBribe, "Fail_Penalty_Seconds", 60);

		detainmentSentenceBaseSeconds           = intVal(sentence, "Base_Seconds", 180);
		detainmentSentencePerWantedLevelSeconds = intVal(sentence, "Per_Wanted_Level_Seconds", 60);

		detainmentFallbackExitWaypoint = str(detainment, "Fallback_Exit_Waypoint", "spawn");

		detainmentBailSuccessSound      = str(detainSounds, "Bail_Success", "BLOCK_NOTE_BLOCK_PLING");
		detainmentBribeSuccessSound     = str(detainSounds, "Bribe_Success", "ENTITY_VILLAGER_YES");
		detainmentBribeFailSound        = str(detainSounds, "Bribe_Fail", "ENTITY_VILLAGER_NO");
		detainmentTransitCommitSound    = str(detainSounds, "Transit_Commit", "BLOCK_IRON_DOOR_CLOSE");
		detainmentSentenceCompleteSound = str(detainSounds, "Sentence_Complete", "BLOCK_BELL_USE");

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
		traderTipAmount              = money(trader, "Tip_Amount", "100");

		// banker
		NodeReader banker = section(root, "Banker", report);
		bankerHeadTrackRadius = intVal(banker, "Head_Track_Radius", 8);
		bankerMaxHealth       = dbl(banker, "Max_Health", 20.0);
		bankerInvulnerable    = bool(banker, "Invulnerable", true);
		bankerFallbackTierId  = str(banker, "Fallback_Tier_Id", "Basic");

		// turf
		NodeReader turf          = section(root, "Turf", report);
		NodeReader turfCapture   = section(turf, "Capture", report);
		NodeReader turfSounds    = section(turfCapture, "Sounds", report);
		NodeReader turfStart     = section(turfSounds, "Start", report);
		NodeReader turfDone      = section(turfSounds, "Complete", report);
		NodeReader turfFailed    = section(turfSounds, "Failed", report);
		NodeReader turfTick      = section(turfSounds, "Tick", report);
		NodeReader turfUnclaimed = section(turfSounds, "Unclaimed", report);

		turfIncomeIntervalMinutes        = intVal(turf, "Income_Interval_Minutes", 10);
		turfDefaultIncomeAmount          = money(turf, "Default_Income_Amount", "100");
		turfWandItemType                 = str(turf, "Wand_Item_Type", "BLAZE_ROD");
		turfVisualizationDurationSeconds = intVal(turf, "Visualization_Duration_Seconds", 30);
		turfVisualizationParticle        = str(turf, "Visualization_Particle", "FLAME");
		turfShowEnterTitle               = bool(turf, "Show_Enter_Title", true);

		turfCaptureDurationSeconds             = intVal(turfCapture, "Duration_Seconds", 180);
		turfCaptureUnclaimedPhase1Seconds      = intVal(turfCapture, "Unclaimed_Phase1_Seconds", 90);
		turfCaptureUnclaimedPhase2Seconds      = intVal(turfCapture, "Unclaimed_Phase2_Seconds", 90);
		turfCaptureCooldownMinutes             = intVal(turfCapture, "Cooldown_Minutes", 15);
		turfCaptureAbandonGraceSeconds         = intVal(turfCapture, "Abandon_Grace_Seconds", 15);
		turfCapturePostLogoffProtectionMinutes = intVal(turfCapture, "Post_Logoff_Protection_Minutes", 10);
		turfCaptureInactivityAutoReleaseDays   = intVal(turfCapture, "Inactivity_Auto_Release_Days", 10);
		turfCaptureSoundEnabled                = bool(turfCapture, "Enable_Sound", true);
		turfCaptureBroadcastGlobally           = bool(turfCapture, "Broadcast_Globally", true);
		turfCaptureProgressMilestones          = intList(turfCapture, "Progress_Milestones");
		if (turfCaptureProgressMilestones.isEmpty()) {
			turfCaptureProgressMilestones = Arrays.asList(25, 50, 75);
		}

		turfCaptureSoundStartName       = str(turfStart, "Name", "BLOCK_NOTE_BLOCK_PLING");
		turfCaptureSoundStartVolume     = dbl(turfStart, "Volume", 1.0);
		turfCaptureSoundStartPitch      = dbl(turfStart, "Pitch", 1.0);
		turfCaptureSoundCompleteName    = str(turfDone, "Name", "UI_TOAST_CHALLENGE_COMPLETE");
		turfCaptureSoundCompleteVolume  = dbl(turfDone, "Volume", 1.0);
		turfCaptureSoundCompletePitch   = dbl(turfDone, "Pitch", 1.0);
		turfCaptureSoundFailedName      = str(turfFailed, "Name", "ENTITY_VILLAGER_NO");
		turfCaptureSoundFailedVolume    = dbl(turfFailed, "Volume", 1.0);
		turfCaptureSoundFailedPitch     = dbl(turfFailed, "Pitch", 1.0);
		turfCaptureSoundTickName        = str(turfTick, "Name", "BLOCK_NOTE_BLOCK_HAT");
		turfCaptureSoundTickVolume      = dbl(turfTick, "Volume", 0.3);
		turfCaptureSoundTickPitch       = dbl(turfTick, "Pitch", 1.8);
		turfCaptureSoundUnclaimedName   = str(turfUnclaimed, "Name", "ENTITY_ENDER_DRAGON_GROWL");
		turfCaptureSoundUnclaimedVolume = dbl(turfUnclaimed, "Volume", 0.5);
		turfCaptureSoundUnclaimedPitch  = dbl(turfUnclaimed, "Pitch", 1.5);

		// turf contribution — points awarded to Member.contribution for turf activity. The gang module
		// persists contribution already; values can later drive a weighted payout of gang turf income.
		NodeReader turfContribution       = section(turf, "Contribution", report);
		NodeReader turfContributionPoints = section(turfContribution, "Points", report);
		turfContributionDefenderPresenceTick = dbl(turfContributionPoints, "Defender_Presence_Tick", 0.5);
		turfContributionAttackerPresenceTick = dbl(turfContributionPoints, "Attacker_Presence_Tick", 1.0);
		turfContributionCaptureCompleteBonus = dbl(turfContributionPoints, "Capture_Complete_Bonus", 50.0);
		turfContributionDefenseSuccessBonus  = dbl(turfContributionPoints, "Defense_Success_Bonus", 25.0);

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
