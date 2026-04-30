package org.luckyraven.gangland.file.configuration.copsncrooks;

import org.luckyraven.gangland.copsncrooks.npc.banker.config.BankerSettings;
import org.luckyraven.gangland.file.configuration.Settings;

import java.math.BigDecimal;

public class BankerSettingsImpl implements BankerSettings {

	@Override
	public int getHeadTrackRadius() {
		return Settings.getBankerHeadTrackRadius();
	}

	@Override
	public double getMaxHealth() {
		return Settings.getBankerMaxHealth();
	}

	@Override
	public boolean isInvulnerable() {
		return Settings.isBankerInvulnerable();
	}

	@Override
	public String getFallbackTierId() {
		return Settings.getBankerFallbackTierId();
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
		return Settings.getBankRenameFee();
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
