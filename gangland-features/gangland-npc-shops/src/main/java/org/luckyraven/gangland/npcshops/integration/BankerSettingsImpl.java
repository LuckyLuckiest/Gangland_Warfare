package org.luckyraven.gangland.npcshops.integration;

import lombok.CustomLog;
import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.npcshops.banker.config.BankerSettings;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Reads {@code npc/banker_settings.yml} (module-owned default, WS4 G1a â€” moved out of the core settings.yml
 * {@code Banker:} block). Bank economy knobs ({@code getResetPeriodSeconds}/{@code getCreateFee}/
 * {@code getInitialBalance} stay delegated to core {@link Settings}. {@code getRenameFee} moved to this file.
 */
@CustomLog
public final class BankerSettingsImpl implements BankerSettings, BeanLifecycle {

	private final FileHandler fileHandler;
	private final FileManager fileManager;

	private volatile int        headTrackRadius;
	private volatile double     maxHealth;
	private volatile boolean    invulnerable;
	private volatile String     fallbackTierId;
	private volatile BigDecimal renameFee;

	public BankerSettingsImpl(FileManager fileManager) {
		try {
			String fileName = "banker_settings";
			fileManager.checkFileLoaded(fileName);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException e) {
			throw new PluginException(e);
		}
		this.fileManager = fileManager;
		load();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		load();
	}

	ConfigReport load() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = FileHandlerReader.read(fileHandler, report);

		headTrackRadius = reader.get("Head_Track_Radius").asInt().orDefault(8);
		maxHealth       = reader.get("Max_Health").asDouble().orDefault(20.0);
		invulnerable    = reader.get("Invulnerable").asBool().orDefault(true);
		fallbackTierId  = reader.get("Fallback_Tier_Id").asString().orDefault("Basic");

		reader.get("Rename_Fee"); // read below through MovedSetting; touched so the unknown-key sweep knows it
		// Rename_Fee moved from core settings.yml; use MovedSetting to fall back for servers still using the old location
		MovedSetting moved = MovedSetting.of(fileHandler, fileManager, "npcshops");
		renameFee = moved.getMoney("Rename_Fee", "User.Bank.Rename_Fee", "1000");

		if (!report.isEmpty()) report.log(log);
		return report;
	}

	@Override
	public int getHeadTrackRadius() {
		return headTrackRadius;
	}

	@Override
	public double getMaxHealth() {
		return maxHealth;
	}

	@Override
	public boolean isInvulnerable() {
		return invulnerable;
	}

	@Override
	public String getFallbackTierId() {
		return fallbackTierId;
	}

	@Override
	public long getResetPeriodSeconds() {
		return Settings.getBankResetPeriodSeconds();
	}

	@Override
	public BigDecimal getCreateFee() {
		return Settings.getBankCreateFee();
	}

	@Override
	public BigDecimal getInitialBalance() {
		return Settings.getBankInitialBalance();
	}

	@Override
	public BigDecimal getRenameFee() {
		return renameFee;
	}

	@Override
	public String getInventoryFillItem() {
		return Settings.getInventoryFillItem();
	}

	@Override
	public String getInventoryFillName() {
		return Settings.getInventoryFillName();
	}

}
