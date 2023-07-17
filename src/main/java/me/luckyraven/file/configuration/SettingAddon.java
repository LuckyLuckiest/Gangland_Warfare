package me.luckyraven.file.configuration;

import lombok.Getter;
import me.luckyraven.file.FileManager;
import org.bukkit.configuration.file.FileConfiguration;

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
	private static boolean gangUse, gangNameDuplicates;
	@Getter
	private static String gangRankHead, gangRankTail;
	@Getter
	private static double gangInitialBalance, gangCreateFee, gangMaxBalance, gangContributionRate;

	public SettingAddon(FileManager fileManager) {
		settings = fileManager.getFile("settings").getFileConfiguration();

		initialize();
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

		gangUse = settings.getBoolean("Gang.Use");
		gangNameDuplicates = settings.getBoolean("Gang.Name_Duplicates");
		gangRankHead = settings.getString("Gang.Rank.Head");
		gangRankTail = settings.getString("Gang.Rank.Tail");
		gangInitialBalance = settings.getDouble("Gang.Account.First_Balance");
		gangCreateFee = settings.getDouble("Gang.Account.Create_Cost");
		gangMaxBalance = settings.getDouble("Gang.Account.Maximum_Balance");
		gangContributionRate = settings.getDouble("Gang.Account.Contribution_Rate");
	}

}
