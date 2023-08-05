package me.luckyraven.file.configuration;

import lombok.Getter;
import me.luckyraven.file.FileManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.Objects;

public class SettingAddon {

	@Getter
	static String inventoryLineItem, inventoryLineName;
	@Getter
	private static FileConfiguration settings;
	// language picked
	@Getter
	private static String languagePicked;
	// database configuration
	@Getter
	private static String databaseType;
	@Getter
	private static String mysqlHost, mysqlUsername, mysqlPassword;
	@Getter
	private static int     mysqlPort;
	@Getter
	private static boolean sqliteBackup, sqliteFailedMysql;
	// inventory configuration
	@Getter
	private static String inventoryFillItem, inventoryFillName;
	// economy
	@Getter
	private static String moneySymbol, balanceFormat;
	@Getter
	private static double playerInitialBalance, playerMaxBalance;
	@Getter
	private static double bankInitialBalance, bankCreateFee, bankMaxBalance;

	// bounty configuration
	@Getter
	private static double bountyEachKillValue, bountyMaxKill;
	@Getter
	private static boolean bountyTimerEnable;
	@Getter
	private static double  bountyTimerMultiple, bountyTimerMax;
	@Getter
	private static int bountyTimeInterval;

	// phone configuration
	@Getter
	private static boolean phoneEnabled;
	@Getter
	private static String  phoneItem, phoneName;
	@Getter
	private static int     phoneSlot;
	@Getter
	private static boolean phoneMovable, phoneDroppable;

	// gang configuration
	@Getter
	private static boolean gangEnable, gangNameDuplicates;
	@Getter
	private static String gangRankHead, gangRankTail, gangDisplayNameChar;
	@Getter
	private static double gangInitialBalance, gangCreateFee, gangMaxBalance, gangContributionRate;

	public SettingAddon(FileManager fileManager) {
		try {
			fileManager.checkFileLoaded("settings");
			settings = Objects.requireNonNull(fileManager.getFile("settings")).getFileConfiguration();

			initialize();
		} catch (IOException exception) {
			throw new RuntimeException(exception);
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

		// inventory
		inventoryFillItem = settings.getString("Inventory.Fill.Item");
		inventoryFillName = settings.getString("Inventory.Fill.Name");
		inventoryLineItem = settings.getString("Inventory.Line.Item");
		inventoryLineName = settings.getString("Inventory.Line.Name");

		// economy
		moneySymbol = Objects.requireNonNull(settings.getString("Money_Symbol")).substring(0, 1);
		balanceFormat = settings.getString("Balance_Format.Format");
		playerInitialBalance = settings.getDouble("Player_Account.First_Balance");
		playerMaxBalance = settings.getDouble("Player_Account.Maximum_Balance");
		bankInitialBalance = settings.getDouble("Bank_Account.First_Balance");
		bankCreateFee = settings.getDouble("Bank_Account.Create_Cost");
		bankMaxBalance = settings.getDouble("Bank_Account.Maximum_Balance");

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
	}

}
