package me.luckyraven.file.configuration;

import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileInitializer;
import me.luckyraven.file.FileManager;
import me.luckyraven.util.utilities.NumberUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SettingAddon implements FileInitializer {

	private static final @Getter Map<String, Object> settingsMap         = new LinkedHashMap<>();
	private static final @Getter Map<String, Object> settingsPlaceholder = new LinkedHashMap<>();

	private static final Logger logger = LogManager.getLogger(SettingAddon.class.getSimpleName());

	private static FileConfiguration settings;

	// update configuration
	private static @Getter boolean updaterEnabled, notifyPrivilegedPlayers, updaterAutoUpdate;

	// language picked
	private static @Getter String languagePicked;

	// resource pack
	private static @Getter boolean resourcePackEnabled;
	private static @Getter String  resourcePackUrl;
	private static @Getter boolean resourcePackKick;

	// database configuration
	private static @Getter String databaseType;
	private static @Getter String mysqlHost, mysqlUsername, mysqlPassword;
	private static @Getter int     mysqlPort;
	private static @Getter boolean sqliteBackup, sqliteFailedMysql, autoSave, autoSaveDebug;
	private static @Getter int autoSaveTime;

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

	// bounty configuration
	private static @Getter double bountyEachKillValue, bountyMaxKill;
	private static @Getter boolean bountyTimerEnabled;
	private static @Getter double  bountyTimerMultiple, bountyTimerMax;
	private static @Getter int bountyTimeInterval;

	// wanted configuration
	private static @Getter double wantedTakeMoneyAmount, wantedTakeMoneyMultiplier, wantedTimerMultiplierAmount;
	private static @Getter boolean wantedEnabled, wantedTimerEnabled, wantedTimerMultiplierEnabled,
			wantedKillComboEnabled;
	private static @Getter int wantedTimerTime, wantedLevelIncrement, wantedMaximumLevel, wantedKillComboResetAfter;
	private static @Getter List<Integer> wantedKillCounter;

	// gang configuration
	private static @Getter boolean gangEnabled, gangNameDuplicates;
	private static @Getter String gangRankHead, gangRankTail, gangDisplayNameChar;
	private static @Getter double gangInitialBalance, gangCreateFee, gangMaxBalance, gangContributionRate;

	// scoreboard configuration
	private static @Getter boolean scoreboardEnabled;
	private static @Getter String  scoreboardDriver;

	// entity marker configuration
	private static @Getter List<String> defaultPoliceEntities, defaultCivilianEntities;
	private static @Getter String policeName, civilianName;
	private static @Getter List<String> policeWearables, civilianWearables;
	private static @Getter double policeExperienceDropsMinimum, policeExperienceDropsMaximum,
			civilianExperienceDropsMinimum, civilianExperienceDropsMaximum;
	private static @Getter List<String> policeItemDrops, civilianItemDrops;

	public SettingAddon(FileManager fileManager) {
		try {
			String fileName = "settings";

			fileManager.checkFileLoaded(fileName);

			FileHandler file = Objects.requireNonNull(fileManager.getFile(fileName));

			settings = file.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	public static String formatDouble(double value) {
		return NumberUtil.formatDouble(format(value), value);
	}

	public static Method getSetting(String methodName) {
		Method[] methods = SettingAddon.class.getDeclaredMethods();

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

	@Override
	public void initialize() {
		try {
			init();
		} catch (Exception exception) {
			// create a new file
			// initialize again
//			init();
		}
	}

	private void init() {
		// update configuration
		var updateChecker = settings.getConfigurationSection("Update_Checker");
		Objects.requireNonNull(updateChecker);

		updaterEnabled          = updateChecker.getBoolean("Enable");
		notifyPrivilegedPlayers = updateChecker.getBoolean("Notify_Privileged_Players");
		updaterAutoUpdate       = updateChecker.getBoolean("Auto_Update");

		// language picked
		languagePicked = settings.getString("Language");

		// resource pack
		var resourcePack = settings.getConfigurationSection("Resource_Pack");
		Objects.requireNonNull(resourcePack);

		resourcePackEnabled = resourcePack.getBoolean("Enable");
		resourcePackUrl     = resourcePack.getString("URL");
		resourcePackKick    = resourcePack.getBoolean("Kick");

		// database
		var database = settings.getConfigurationSection("Database");
		Objects.requireNonNull(database);

		databaseType      = database.getString("Type");
		mysqlHost         = database.getString("MySQL.Host");
		mysqlUsername     = database.getString("MySQL.Username");
		mysqlPassword     = database.getString("MySQL.Password");
		mysqlPort         = database.getInt("MySQL.Port");
		sqliteBackup      = database.getBoolean("SQLite.Backup");
		sqliteFailedMysql = database.getBoolean("SQLite.Failed_MySQL");
		autoSave          = database.getBoolean("Auto_Save.Enable");
		autoSaveDebug     = database.getBoolean("Auto_Save.Debug");
		autoSaveTime      = database.getInt("Auto_Save.Time");

		// inventory
		var inventory = settings.getConfigurationSection("Inventory");
		Objects.requireNonNull(inventory);

		inventoryFillItem = inventory.getString("Fill.Item");
		inventoryFillName = inventory.getString("Fill.Name");
		inventoryLineItem = inventory.getString("Line.Item");
		inventoryLineName = inventory.getString("Line.Name");

		var multiInventory = inventory.getConfigurationSection("Multi_Inventory");
		Objects.requireNonNull(multiInventory);

		nextPage     = multiInventory.getString("Next_Page");
		previousPage = multiInventory.getString("Previous_Page");
		homePage     = multiInventory.getString("Home_Page");

		// economy
		moneySymbol   = Objects.requireNonNull(settings.getString("Money_Symbol")).substring(0, 1);
		balanceFormat = settings.getString("Balance_Format.Format");

		// user
		var user = settings.getConfigurationSection("User");
		Objects.requireNonNull(user);

		userInitialBalance = user.getDouble("Account.Initial_Balance");
		userMaxBalance     = user.getDouble("Account.Maximum_Balance");
		bankInitialBalance = user.getDouble("Bank.Initial_Balance");
		bankCreateFee      = user.getDouble("Bank.Create_Cost");
		bankMaxBalance     = user.getDouble("Bank.Maximum_Balance");
		// user levels
		userMaxLevel         = user.getInt("Level.Maximum_Level");
		userLevelBaseAmount  = user.getInt("Level.Base_Amount");
		userLevelFormula     = user.getString("Level.Formula");
		userSkillUpgrade     = user.getInt("Level.Skill.Upgrade");
		userSkillCost        = user.getDouble("Level.Skill.Cost");
		userSkillExponential = user.getDouble("Level.Skill.Exponential");
		// user death
		deathEnabled                 = user.getBoolean("Death.Enable");
		deathMoneyCommandEnabled     = user.getBoolean("Death.Money.Command.Enable");
		deathMoneyCommandExecutables = user.getStringList("Death.Money.Command.Executable");
		deathLoseMoney               = !user.getBoolean("Death.Lose_Money");
		deathLoseMoneyFormula        = user.getString("Death.Money.Formula");
		deathThreshold               = user.getDouble("Death.Money.Threshold");

		// bounty
		var bounty = settings.getConfigurationSection("Bounty");
		Objects.requireNonNull(bounty);

		bountyEachKillValue = bounty.getDouble("Kill.Each");
		bountyMaxKill       = bounty.getDouble("Kill.Maximum");
		bountyTimerEnabled  = bounty.getBoolean("Repeating_Timer.Enable");
		bountyTimerMultiple = bounty.getDouble("Repeating_Timer.Multiple");
		bountyTimeInterval  = bounty.getInt("Repeating_Timer.Time");
		bountyTimerMax      = bounty.getDouble("Repeating_Timer.Maximum");

		// wanted
		var wanted = settings.getConfigurationSection("Wanted");
		Objects.requireNonNull(wanted);

		wantedEnabled = wanted.getBoolean("Enable");

		var wantedTakeMoney = wanted.getConfigurationSection("Take_Money");
		Objects.requireNonNull(wantedTakeMoney);

		wantedTakeMoneyAmount     = wantedTakeMoney.getDouble("Amount");
		wantedTakeMoneyMultiplier = wantedTakeMoney.getDouble("Multiplier");

		var wantedTimer = wanted.getConfigurationSection("Repeating_Timer");
		Objects.requireNonNull(wantedTimer);

		wantedTimerEnabled           = wantedTimer.getBoolean("Enable");
		wantedTimerTime              = wantedTimer.getInt("Time");
		wantedTimerMultiplierEnabled = wantedTimer.getBoolean("Multiplier.Enable");
		wantedTimerMultiplierAmount  = wantedTimer.getDouble("Multiplier.Amount");

		var wantedLevel = wanted.getConfigurationSection("Level");
		Objects.requireNonNull(wantedLevel);

		wantedLevelIncrement = wantedLevel.getInt("Increment");
		wantedMaximumLevel   = wantedLevel.getInt("Maximum");

		var wantedKillCombo = wanted.getConfigurationSection("Kill_Combo");
		Objects.requireNonNull(wantedKillCombo);

		wantedKillComboEnabled    = wantedKillCombo.getBoolean("Enable");
		wantedKillComboResetAfter = wantedKillCombo.getInt("Reset_After");
		wantedKillCounter         = wantedKillCombo.getIntegerList("Kill_Counter");

		// gang
		gangEnabled          = settings.getBoolean("Gang.Enable");
		gangNameDuplicates   = settings.getBoolean("Gang.Name_Duplicates");
		gangRankHead         = settings.getString("Gang.Rank.Head");
		gangRankTail         = settings.getString("Gang.Rank.Tail");
		gangDisplayNameChar  = Objects.requireNonNull(settings.getString("Gang.Display_Name_Char")).substring(0, 1);
		gangInitialBalance   = settings.getDouble("Gang.Account.Initial_Balance");
		gangCreateFee        = settings.getDouble("Gang.Account.Create_Cost");
		gangMaxBalance       = settings.getDouble("Gang.Account.Maximum_Balance");
		gangContributionRate = settings.getDouble("Gang.Account.Contribution_Rate");

		// scoreboard
		scoreboardEnabled = settings.getBoolean("Scoreboard.Enable");
		scoreboardDriver  = settings.getString("Scoreboard.Driver");

		// entity marker
		var entityMarker = settings.getConfigurationSection("Entity_Marker");
		Objects.requireNonNull(entityMarker);

		var police = entityMarker.getConfigurationSection("Police");
		Objects.requireNonNull(police);

		defaultPoliceEntities = police.getStringList("Default_Entities");
		policeName            = police.getString("Name");
		policeWearables       = police.getStringList("Wear");

		var policeDrops = police.getConfigurationSection("Drops");
		Objects.requireNonNull(policeDrops);

		policeExperienceDropsMinimum = policeDrops.getDouble("Experience.Minimum");
		policeExperienceDropsMaximum = policeDrops.getDouble("Experience.Maximum");
		policeItemDrops              = policeDrops.getStringList("Items");

		var civilian = entityMarker.getConfigurationSection("Civilian");
		Objects.requireNonNull(civilian);

		defaultCivilianEntities = civilian.getStringList("Default_Entities");
		civilianName            = civilian.getString("Name");
		civilianWearables       = civilian.getStringList("Wear");

		var civilianDrops = civilian.getConfigurationSection("Drops");
		Objects.requireNonNull(civilianDrops);

		civilianExperienceDropsMinimum = civilianDrops.getDouble("Experience.Minimum");
		civilianExperienceDropsMaximum = civilianDrops.getDouble("Experience.Maximum");
		civilianItemDrops              = civilianDrops.getStringList("Items");

		addEachFieldReflection();
		convertToPlaceholder();
	}

	private void addEachFieldReflection() {
		Field[] fields = this.getClass().getDeclaredFields();

		for (Field field : fields) {
			field.setAccessible(true);
			try {
				Object value = field.get(null);

				settingsMap.put(field.getName(), value);
			} catch (IllegalAccessException exception) {
				logger.error(exception);
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
