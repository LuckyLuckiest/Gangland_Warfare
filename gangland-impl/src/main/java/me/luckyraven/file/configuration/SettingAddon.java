package me.luckyraven.file.configuration;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.FileInitializer;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@CustomLog
public class SettingAddon implements FileInitializer {

	private static final @Getter Map<String, Object> settingsMap         = new LinkedHashMap<>();
	private static final @Getter Map<String, Object> settingsPlaceholder = new LinkedHashMap<>();

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
	private static @Getter List<String> defaultCivilianEntities;
	private static @Getter String       civilianName;
	private static @Getter List<String> civilianWearables;
	private static @Getter double       civilianExperienceDropsMinimum, civilianExperienceDropsMaximum;
	private static @Getter List<String> civilianItemDrops;

	// cop count configuration
	private static @Getter boolean copCountFormulaEnabled;
	private static @Getter String  copCountFormula;
	private static @Getter int     copCountBase, copCountPerLevel, copCountMax;

	// loot chest configuration
	private static @Getter long   lootChestCountdownTimer;
	private static @Getter String lootChestOpeningSound, lootChestLockedSound, lootChestClosingSound;
	private static @Getter List<String> lootChestAllowedBlocks;
	private static @Getter double       lootChestRewardMoneyMinimum, lootChestRewardMoneyMaximum,
			lootChestRewardExperienceMinimum, lootChestRewardExperienceMaximum;
	private static @Getter List<String> lootChestRewardCommands;

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

		updaterEnabled          = updateChecker.getBoolean("Enable", true);
		notifyPrivilegedPlayers = updateChecker.getBoolean("Notify_Privileged_Players", false);
		updaterAutoUpdate       = updateChecker.getBoolean("Auto_Update", true);

		// language picked
		languagePicked = settings.getString("Language", "en");

		// resource pack
		var resourcePack = settings.getConfigurationSection("Resource_Pack");
		Objects.requireNonNull(resourcePack);

		resourcePackEnabled = resourcePack.getBoolean("Enable", true);
		resourcePackUrl     = resourcePack.getString("URL", "");
		resourcePackKick    = resourcePack.getBoolean("Kick", false);

		// database
		var database = settings.getConfigurationSection("Database");
		Objects.requireNonNull(database);

		databaseType      = database.getString("Type", "sqlite");
		mysqlHost         = database.getString("MySQL.Host", "localhost");
		mysqlUsername     = database.getString("MySQL.Username", "root");
		mysqlPassword     = database.getString("MySQL.Password", "");
		mysqlPort         = database.getInt("MySQL.Port", 3306);
		sqliteBackup      = database.getBoolean("SQLite.Backup", true);
		sqliteFailedMysql = database.getBoolean("SQLite.Failed_MySQL", true);
		autoSave          = database.getBoolean("Auto_Save.Enable", true);
		autoSaveDebug     = database.getBoolean("Auto_Save.Debug", true);
		autoSaveTime      = database.getInt("Auto_Save.Time", 10);
		cleanUpTime       = database.getDouble("Clean_Up.Time", 30);

		// inventory
		var inventory = settings.getConfigurationSection("Inventory");
		Objects.requireNonNull(inventory);

		inventoryFillItem = inventory.getString("Fill.Item", "BLACK_STAINED_GLASS_PANE");
		inventoryFillName = inventory.getString("Fill.Name", " ");
		inventoryLineItem = inventory.getString("Line.Item", "WHITE_STAINED_GLASS_PANE");
		inventoryLineName = inventory.getString("Line.Name", " ");

		var multiInventory = inventory.getConfigurationSection("Multi_Inventory");
		Objects.requireNonNull(multiInventory);

		nextPage     = multiInventory.getString("Next_Page",
												"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYzMzlmZjJlNTM0MmJhMThiZGM0OGE5OWNjYTY1ZDEyM2NlNzgxZDg3ODI3MmY5ZDk2NGVhZDNiOGFkMzcwIn19fQ==");
		previousPage = multiInventory.getString("Previous_Page",
												"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg0ZjU5NzEzMWJiZTI1ZGMwNThhZjg4OGNiMjk4MzFmNzk1OTliYzY3Yzk1YzgwMjkyNWNlNGFmYmEzMzJmYyJ9fX0=");
		homePage     = multiInventory.getString("Home_Page",
												"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg0ZjU5NzEzMWJiZTI1ZGMwNThhZjg4OGNiMjk4MzFmNzk1OTliYzY3Yzk1YzgwMjkyNWNlNGFmYmEzMzJmYyJ9fX0=");

		// economy
		moneySymbol   = settings.getString("Money_Symbol", "$").substring(0, 1);
		balanceFormat = settings.getString("Balance_Format.Format", "%,.2f");

		// user
		var user = settings.getConfigurationSection("User");
		Objects.requireNonNull(user);

		userInitialBalance = user.getDouble("Account.Initial_Balance", 0);
		userMaxBalance     = user.getDouble("Account.Maximum_Balance", 10_000_000);
		bankInitialBalance = user.getDouble("Bank.Initial_Balance", 0);
		bankCreateFee      = user.getDouble("Bank.Create_Cost", 5_000);
		bankMaxBalance     = user.getDouble("Bank.Maximum_Balance", 1_000_000_000);
		// user levels
		userMaxLevel         = user.getInt("Level.Maximum_Level", 100);
		userLevelBaseAmount  = user.getInt("Level.Base_Amount", 1_000);
		userLevelFormula     = user.getString("Level.Formula", "base * level ^ 1.5");
		userSkillUpgrade     = user.getInt("Level.Skill.Upgrade", 1);
		userSkillCost        = user.getDouble("Level.Skill.Cost", 500);
		userSkillExponential = user.getDouble("Level.Skill.Exponential", 1.8);
		// user death
		deathEnabled                 = user.getBoolean("Death.Enable", true);
		deathMoneyCommandEnabled     = user.getBoolean("Death.Money.Command.Enable", false);
		deathMoneyCommandExecutables = user.getStringList("Death.Money.Command.Executable");
		deathLoseMoney               = !user.getBoolean("Death.Lose_Money", true);
		deathLoseMoneyFormula        = user.getString("Death.Money.Formula", "balance * 0.15");
		deathThreshold               = user.getDouble("Death.Money.Threshold", 1_000);

		// bounty
		var bounty = settings.getConfigurationSection("Bounty");
		Objects.requireNonNull(bounty);

		bountyEachKillValue = bounty.getDouble("Kill.Each", 5);
		bountyMaxKill       = bounty.getDouble("Kill.Maximum", 50_000);
		bountyTimerEnabled  = bounty.getBoolean("Repeating_Timer.Enable", true);
		bountyTimerMultiple = bounty.getDouble("Repeating_Timer.Multiple", 2);
		bountyTimeInterval  = bounty.getInt("Repeating_Timer.Time", 300);
		bountyTimerMax      = bounty.getDouble("Repeating_Timer.Maximum", 20_000);

		// wanted
		var wanted = settings.getConfigurationSection("Wanted");
		Objects.requireNonNull(wanted);

		wantedEnabled = wanted.getBoolean("Enable", true);

		var wantedTakeMoney = wanted.getConfigurationSection("Take_Money");
		Objects.requireNonNull(wantedTakeMoney);

		wantedTakeMoneyAmount     = wantedTakeMoney.getDouble("Amount", 50);
		wantedTakeMoneyMultiplier = wantedTakeMoney.getDouble("Multiplier", 5);

		var wantedTimer = wanted.getConfigurationSection("Repeating_Timer");
		Objects.requireNonNull(wantedTimer);

		wantedTimerEnabled           = wantedTimer.getBoolean("Enable", true);
		wantedTimerTime              = wantedTimer.getInt("Time", 120);
		wantedTimerMultiplierEnabled = wantedTimer.getBoolean("Multiplier.Enable", true);
		wantedTimerMultiplierAmount  = wantedTimer.getDouble("Multiplier.Amount", 1.1);

		var wantedLevel = wanted.getConfigurationSection("Level");
		Objects.requireNonNull(wantedLevel);

		wantedLevelIncrement = wantedLevel.getInt("Increment", 1);
		wantedMaximumLevel   = wantedLevel.getInt("Maximum", 5);

		var wantedKillCombo = wanted.getConfigurationSection("Kill_Combo");
		Objects.requireNonNull(wantedKillCombo);

		wantedKillComboEnabled    = wantedKillCombo.getBoolean("Enable", true);
		wantedKillComboResetAfter = wantedKillCombo.getInt("Reset_After", 10);
		wantedKillCounter         = wantedKillCombo.getIntegerList("Kill_Counter");

		// gang
		gangEnabled          = settings.getBoolean("Gang.Enable", true);
		gangNameDuplicates   = settings.getBoolean("Gang.Name_Duplicates", false);
		gangRankHead         = settings.getString("Gang.Rank.Head", "member");
		gangRankTail         = settings.getString("Gang.Rank.Tail", "owner");
		gangDisplayNameChar  = settings.getString("Gang.Display_Name_Char", "*").substring(0, 1);
		gangInitialBalance   = settings.getDouble("Gang.Account.Initial_Balance", 0);
		gangCreateFee        = settings.getDouble("Gang.Account.Create_Cost", 100_000);
		gangMaxBalance       = settings.getDouble("Gang.Account.Maximum_Balance", 100_000_000_000.0);
		gangContributionRate = settings.getDouble("Gang.Account.Contribution_Rate", 1_000);

		// scoreboard
		scoreboardEnabled = settings.getBoolean("Scoreboard.Enable", true);
		scoreboardDriver  = settings.getString("Scoreboard.Driver", "Driver_V3");

		// entity marker
		var entityMarker = settings.getConfigurationSection("Entity_Marker");
		Objects.requireNonNull(entityMarker);

		var civilian = entityMarker.getConfigurationSection("Civilian");
		Objects.requireNonNull(civilian);

		defaultCivilianEntities = civilian.getStringList("Default_Entities");
		civilianName            = civilian.getString("Name", "&7Civilian");
		civilianWearables       = civilian.getStringList("Wear");

		var civilianDrops = civilian.getConfigurationSection("Drops");
		Objects.requireNonNull(civilianDrops);

		civilianExperienceDropsMinimum = civilianDrops.getDouble("Experience.Minimum", 1);
		civilianExperienceDropsMaximum = civilianDrops.getDouble("Experience.Maximum", 15);
		civilianItemDrops              = civilianDrops.getStringList("Items");

		// cop count
		var copsCount = settings.getConfigurationSection("Cops.Count");
		Objects.requireNonNull(copsCount);

		copCountFormulaEnabled = copsCount.getBoolean("Formula_Enabled", false);
		copCountFormula        = copsCount.getString("Formula", "base + (level - 1) * perLevel");
		copCountBase           = copsCount.getInt("Base", 2);
		copCountPerLevel       = copsCount.getInt("Per_Level", 1);
		copCountMax            = copsCount.getInt("Max", 8);

		// loot chest
		var lootChest = settings.getConfigurationSection("Loot_Chest");
		Objects.requireNonNull(lootChest);

		lootChestCountdownTimer = lootChest.getLong("Countdown_Timer", 300);
		lootChestOpeningSound   = lootChest.getString("Sound.Opening", "BLOCK_CHEST_OPEN");
		lootChestLockedSound    = lootChest.getString("Sound.Locked", "BLOCK_CHEST_LOCKED");
		lootChestClosingSound   = lootChest.getString("Sound.Closing", "BLOCK_CHEST_CLOSE");
		lootChestAllowedBlocks  = lootChest.getStringList("Allowed_Blocks");

		var lootChestRewards = lootChest.getConfigurationSection("Rewards");
		Objects.requireNonNull(lootChestRewards);

		lootChestRewardMoneyMinimum      = lootChestRewards.getDouble("Money.Minimum", 10);
		lootChestRewardMoneyMaximum      = lootChestRewards.getDouble("Money.Maximum", 1_000);
		lootChestRewardExperienceMinimum = lootChestRewards.getDouble("Experience.Minimum", 5);
		lootChestRewardExperienceMaximum = lootChestRewards.getDouble("Experience.Maximum", 100);
		lootChestRewardCommands          = lootChestRewards.getStringList("Commands");

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
