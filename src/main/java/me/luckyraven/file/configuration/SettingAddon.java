package me.luckyraven.file.configuration;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.FileManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class SettingAddon {

	private static final @Getter Map<String, Object> settingsMap         = new LinkedHashMap<>();
	private static final @Getter Map<String, Object> settingsPlaceholder = new LinkedHashMap<>();

	private static @Getter FileConfiguration settings;
	// language picked
	private static @Getter String            languagePicked;
	// database configuration
	private static @Getter String            databaseType;
	private static @Getter String            mysqlHost, mysqlUsername, mysqlPassword;
	private static @Getter int     mysqlPort;
	private static @Getter boolean sqliteBackup, sqliteFailedMysql, autoSave;
	private static @Getter int    autoSaveTime;
	// inventory configuration
	private static @Getter String inventoryFillItem, inventoryFillName, inventoryLineItem, inventoryLineName;
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
	private static @Getter boolean deathEnable, deathMoneyCommandEnable, deathLoseMoney;
	private static @Getter String deathMoneyCommandExecutable, deathLoseMoneyFormula;
	// bounty configuration
	private static @Getter double bountyEachKillValue, bountyMaxKill;
	private static @Getter boolean bountyTimerEnable;
	private static @Getter double  bountyTimerMultiple, bountyTimerMax;
	private static @Getter int     bountyTimeInterval;
	// phone configuration
	private static @Getter boolean phoneEnabled;
	private static @Getter String  phoneItem, phoneName;
	private static @Getter int     phoneSlot;
	private static @Getter boolean phoneMovable, phoneDroppable;
	// gang configuration
	private static @Getter boolean gangEnable, gangNameDuplicates;
	private static @Getter String gangRankHead, gangRankTail, gangDisplayNameChar;
	private static @Getter double gangInitialBalance, gangCreateFee, gangMaxBalance, gangContributionRate;

	public SettingAddon(FileManager fileManager) {
		try {
			fileManager.checkFileLoaded("settings");
			settings = Objects.requireNonNull(fileManager.getFile("settings")).getFileConfiguration();

			initialize();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	public static String formatDouble(double value) {
		if (settings.getBoolean("Balance_Format.Enable")) return String.format(balanceFormat, value);
		return String.valueOf(value);
	}

	private void initialize() {
		// language picked
		languagePicked = settings.getString("Language");

		// database
		databaseType = settings.getString("Database.Type");
		mysqlHost = settings.getString("Database.MySQL.Host");
		mysqlUsername = settings.getString("Database.MySQL.Username");
		mysqlPassword = settings.getString("Database.MySQL.Password");
		mysqlPort = settings.getInt("Database.MySQL.Port");
		sqliteBackup = settings.getBoolean("Database.SQLite.Backup");
		sqliteFailedMysql = settings.getBoolean("Database.SQLite.Failed_MySQL");
		autoSave = settings.getBoolean("Database.Auto_Save.Enable");
		autoSaveTime = settings.getInt("Database.Auto_Save.Time");

		// inventory
		inventoryFillItem = settings.getString("Inventory.Fill.Item");
		inventoryFillName = settings.getString("Inventory.Fill.Name");
		inventoryLineItem = settings.getString("Inventory.Line.Item");
		inventoryLineName = settings.getString("Inventory.Line.Name");

		// economy
		moneySymbol = Objects.requireNonNull(settings.getString("Money_Symbol")).substring(0, 1);
		balanceFormat = settings.getString("Balance_Format.Format");

		// user
		userInitialBalance = settings.getDouble("User.Account.Initial_Balance");
		userMaxBalance = settings.getDouble("User.Account.Maximum_Balance");
		bankInitialBalance = settings.getDouble("User.Bank.Initial_Balance");
		bankCreateFee = settings.getDouble("User.Bank.Create_Cost");
		bankMaxBalance = settings.getDouble("User.Bank.Maximum_Balance");
		// user levels
		userMaxLevel = settings.getInt("User.Level.Maximum_Level");
		userLevelBaseAmount = settings.getInt("User.Level.Base_Amount");
		userLevelFormula = settings.getString("User.Level.Formula");
		userSkillUpgrade = settings.getInt("User.Level.Skill.Upgrade");
		userSkillCost = settings.getDouble("User.Level.Skill.Cost");
		userSkillExponential = settings.getDouble("User.Level.Skill.Exponential");
		// user death
		deathEnable = settings.getBoolean("User.Death.Enable");
		deathMoneyCommandEnable = settings.getBoolean("User.Death.Money.Command.Enable");
		deathMoneyCommandExecutable = settings.getString("User.Death.Money.Command.Executable");
		deathLoseMoney = settings.getBoolean("User.Death.Lose_Money");
		deathLoseMoneyFormula = settings.getString("User.Death.Money.Formula");

		// bounty
		bountyEachKillValue = settings.getDouble("Bounty.Kill.Each");
		bountyMaxKill = settings.getDouble("Bounty.Kill.Max");
		bountyTimerEnable = settings.getBoolean("Bounty.Repeating_Timer.Enable");
		bountyTimerMultiple = settings.getDouble("Bounty.Repeating_Timer.Multiple");
		bountyTimeInterval = settings.getInt("Bounty.Repeating_Timer.Time");
		bountyTimerMax = settings.getDouble("Bounty.Repeating_Timer.Max");

		// phone
		phoneEnabled = settings.getBoolean("Phone.Enable");
		phoneItem = settings.getString("Phone.Item");
		phoneName = settings.getString("Phone.Name");
		phoneSlot = settings.getInt("Phone.Slot");
		phoneMovable = !settings.getBoolean("Phone.Movable");
		phoneDroppable = !settings.getBoolean("Phone.Droppable");

		// gang
		gangEnable = settings.getBoolean("Gang.Enable");
		gangNameDuplicates = settings.getBoolean("Gang.Name_Duplicates");
		gangRankHead = settings.getString("Gang.Rank.Head");
		gangRankTail = settings.getString("Gang.Rank.Tail");
		gangDisplayNameChar = Objects.requireNonNull(settings.getString("Gang.Display_Name_Char")).substring(0, 1);
		gangInitialBalance = settings.getDouble("Gang.Account.First_Balance");
		gangCreateFee = settings.getDouble("Gang.Account.Create_Cost");
		gangMaxBalance = settings.getDouble("Gang.Account.Maximum_Balance");
		gangContributionRate = settings.getDouble("Gang.Account.Contribution_Rate");

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
				Gangland.getLog4jLogger().error(exception);
			}
		}

		// need to remove the map in order to show the other values separately
		settingsMap.remove("settingsMap");
		settingsMap.remove("settingsPlaceholder");
		settingsMap.remove("settings");
	}

	private void convertToPlaceholder() {
		for (Map.Entry<String, Object> entry : settingsMap.entrySet()) {
			String        key         = entry.getKey();
			Object        value       = entry.getValue();
			StringBuilder modifiedKey = new StringBuilder();

			int change = 0;
			for (int i = 0; i < key.length(); i++) {
				char c = key.charAt(i);

				if (Character.isUpperCase(c)) {
					modifiedKey.append(key, change, i).append("_").append(Character.toLowerCase(c));

					char[] chars = key.toCharArray();

					chars[i] = Character.toLowerCase(chars[i]);
					key = String.valueOf(chars);

					change = i + 1;
				}
			}

			if (change <= key.length()) modifiedKey.append(key.substring(change));

			settingsPlaceholder.put(modifiedKey.toString(), value);
		}
	}

}
