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
	private static double bountyInitialValue, bountyMultiple, bountyMaxValue;
	@Getter
	private static int bountyTimeInterval;

	@Getter
	private static boolean gangUse, gangNameDuplicates;
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
		if (settings.getBoolean("Balance_Format.Use")) return String.format(balanceFormat, value);
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

		bountyInitialValue = settings.getDouble("Bounty.Kill_Value");
		bountyMultiple = settings.getDouble("Bounty.Multiple");
		bountyTimeInterval = settings.getInt("Bounty.Time");
		bountyMaxValue = settings.getDouble("Bounty.Limit");

		gangUse = settings.getBoolean("Gang.Use");
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
