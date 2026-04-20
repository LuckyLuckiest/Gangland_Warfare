package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.file.configuration.Settings;

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
	public double getDailyDepositLimit() {
		return Settings.getBankDailyDepositLimit();
	}

	@Override
	public double getDailyWithdrawLimit() {
		return Settings.getBankDailyWithdrawLimit();
	}

	@Override
	public long getResetPeriodSeconds() {
		return Settings.getBankResetPeriodSeconds();
	}

	@Override
	public double getCreateFee() {
		return Settings.getBankCreateFee();
	}

	@Override
	public double getInitialBalance() {
		return Settings.getBankInitialBalance();
	}

	@Override
	public double getRenameFee() {
		return Settings.getBankRenameFee();
	}

	@Override
	public double getDeleteFee() {
		return Settings.getBankDeleteFee();
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
