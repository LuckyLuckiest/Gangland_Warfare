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
	private static double gangInitialBalance, gangCreateFee, gangMaxBalance;
	@Getter
	private static boolean gangNameDuplicates;

	public SettingAddon(FileManager fileManager) {
		settings = fileManager.getFile("settings").getFileConfiguration();

		initialize();
	}

	private void initialize() {
		moneySymbol = Objects.requireNonNull(settings.getString("Money_Symbol")).substring(0, 1);

		balanceFormat = settings.getString("Balance_Format.Format");

		playerInitialBalance = settings.getDouble("Player_Account.First_Balance");
		playerMaxBalance = settings.getDouble("Player_Account.Maximum_Balance");

		bankInitialBalance = settings.getDouble("Bank_Account.First_Balance");
		bankCreateFee = settings.getDouble("Bank_Account.Create_Cost");
		bankMaxBalance = settings.getDouble("Bank_Account.Maximum_Balance");

		gangInitialBalance = settings.getDouble("Gang_Account.First_Balance");
		gangCreateFee = settings.getDouble("Gang_Account.Create_Cost");
		gangMaxBalance = settings.getDouble("Gang_Account.Maximum_Balance");
		gangNameDuplicates = settings.getBoolean("Gang_Account.Name_Duplicates");
	}

}
