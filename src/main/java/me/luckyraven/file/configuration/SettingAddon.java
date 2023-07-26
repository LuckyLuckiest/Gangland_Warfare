package me.luckyraven.file.configuration;

import lombok.Getter;
import me.luckyraven.file.FileManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.Objects;

public class SettingAddon {

	@Getter
	private static FileConfiguration settings;

	@Getter
	private static String moneySymbol, balanceFormat;

	@Getter
	private static double playerInitialBalance, playerMaxBalance;

	@Getter
	private static double bankInitialBalance, bankCreateFee, bankMaxBalance;

	@Getter
	private static double bountyEachKillValue, bountyMaxKill;
	@Getter
	private static boolean bountyTimerEnable;
	@Getter
	private static double  bountyTimerMultiple, bountyTimerMax;
	@Getter
	private static int bountyTimeInterval;

	@Getter
	private static String  phoneItem;
	@Getter
	private static int     phoneSlot;
	@Getter
	private static boolean phoneMovable, phoneDroppable;

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
		moneySymbol = Objects.requireNonNull(settings.getString("Money_Symbol")).substring(0, 1);

		balanceFormat = settings.getString("Balance_Format.Format");

		playerInitialBalance = settings.getDouble("Player_Account.First_Balance");
		playerMaxBalance = settings.getDouble("Player_Account.Maximum_Balance");

		bankInitialBalance = settings.getDouble("Bank_Account.First_Balance");
		bankCreateFee = settings.getDouble("Bank_Account.Create_Cost");
		bankMaxBalance = settings.getDouble("Bank_Account.Maximum_Balance");

		bountyEachKillValue = settings.getDouble("Bounty.Kill.Each");
		bountyMaxKill = settings.getDouble("Bounty.Kill.Max");
		bountyTimerEnable = settings.getBoolean("Bounty.Repeating_Timer.Enable");
		bountyTimerMultiple = settings.getDouble("Bounty.Repeating_Timer.Multiple");
		bountyTimeInterval = settings.getInt("Bounty.Repeating_Timer.Time");
		bountyTimerMax = settings.getDouble("Bounty.Repeating_Timer.Max");

		phoneItem = settings.getString("Phone.Item");
		phoneSlot = settings.getInt("Phone.Slot");
		phoneMovable = !settings.getBoolean("Phone.Movable");
		phoneDroppable = !settings.getBoolean("Phone.Droppable");

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
