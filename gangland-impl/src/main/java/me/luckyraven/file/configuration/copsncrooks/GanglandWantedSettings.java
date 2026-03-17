package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.wanted.WantedSettings;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.SettingAddon;

/**
 * {@link WantedSettings} implementation backed by {@link SettingAddon} and {@link Messages}.
 */
public class GanglandWantedSettings implements WantedSettings {

	@Override
	public boolean isTimerMultiplierEnabled() {
		return SettingAddon.isWantedTimerMultiplierEnabled();
	}

	@Override
	public double getTimerMultiplierAmount() {
		return SettingAddon.getWantedTimerMultiplierAmount();
	}

	@Override
	public int getTimerTime() {
		return SettingAddon.getWantedTimerTime();
	}

	@Override
	public double getTakeMoneyAmount() {
		return SettingAddon.getWantedTakeMoneyAmount();
	}

	@Override
	public double getTakeMoneyMultiplier() {
		return SettingAddon.getWantedTakeMoneyMultiplier();
	}

	@Override
	public String getWantedDecreasedMessageTemplate() {
		return Messages.WANTED_DECREASED.toString();
	}

	@Override
	public String formatMoneyLoss(double amount) {
		return "&c&l-" + SettingAddon.getMoneySymbol() + SettingAddon.formatDouble(amount);
	}
}
