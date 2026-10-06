package org.luckyraven.gangland.gang.file;

import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.gangland.gang.contract.GangSettingsContract;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.math.BigDecimal;

/**
 * {@link GangSettingsContract} backed by the module's own {@code gang/gang_settings.yml} (0.15.1: moved out of the
 * core {@code settings.yml} {@code Gang:} section). Every key goes through {@link MovedSetting}, so a server whose
 * {@code settings.yml} still sets the old key keeps working for one release. Parsed in {@link #initialize()} (boot and
 * {@code /glw reload}) and cached; the getters never touch the file.
 *
 * <p>{@code Gang.Enable} stays in the core file: the civilians module also reads it.
 */
public final class GanglandGangSettings implements GangSettingsContract, FileInitializer {

	private final FileHandler fileHandler;
	private final FileManager fileManager;

	private boolean    nameDuplicates   = false;
	private String     displayNameChar  = "*";
	private String     rankHead         = "member";
	private String     rankTail         = "owner";
	private BigDecimal initialBalance   = BigDecimal.ZERO;
	private BigDecimal createFee        = new BigDecimal("100000");
	private BigDecimal maxBalance       = new BigDecimal("100000000000");
	private double     contributionRate = 1_000;

	public GanglandGangSettings(FileHandler fileHandler, FileManager fileManager) {
		this.fileHandler = fileHandler;
		this.fileManager = fileManager;
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		MovedSetting moved = MovedSetting.of(fileHandler, fileManager, "gang");

		nameDuplicates = moved.getBoolean("Name_Duplicates", "Gang.Name_Duplicates", false);
		rankHead       = moved.getString("Rank.Head", "Gang.Rank.Head", "member");
		rankTail       = moved.getString("Rank.Tail", "Gang.Rank.Tail", "owner");

		String displayChar = moved.getString("Display_Name_Char", "Gang.Display_Name_Char", "*");
		displayNameChar = displayChar == null || displayChar.isEmpty() ? "*" : displayChar.substring(0, 1);

		initialBalance   = moved.getMoney("Account.Initial_Balance", "Gang.Account.Initial_Balance", "0");
		createFee        = moved.getMoney("Account.Create_Cost", "Gang.Account.Create_Cost", "100000");
		maxBalance       = moved.getMoney("Account.Maximum_Balance", "Gang.Account.Maximum_Balance", "100000000000");
		contributionRate = moved.getDouble("Account.Contribution_Rate", "Gang.Account.Contribution_Rate", 1_000);
	}

	@Override
	public boolean isGangNameDuplicates() {
		return nameDuplicates;
	}

	@Override
	public String getGangDisplayNameChar() {
		return displayNameChar;
	}

	@Override
	public String getGangRankHead() {
		return rankHead;
	}

	@Override
	public String getGangRankTail() {
		return rankTail;
	}

	@Override
	public BigDecimal getGangInitialBalance() {
		return initialBalance;
	}

	@Override
	public BigDecimal getGangCreateFee() {
		return createFee;
	}

	@Override
	public BigDecimal getGangMaxBalance() {
		return maxBalance;
	}

	@Override
	public double getGangContributionRate() {
		return contributionRate;
	}
}
