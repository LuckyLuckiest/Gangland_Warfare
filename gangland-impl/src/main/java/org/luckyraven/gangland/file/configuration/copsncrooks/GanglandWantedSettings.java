package org.luckyraven.gangland.file.configuration.copsncrooks;

import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.wanted.WantedSettings;

import java.math.BigDecimal;

/**
 * {@link WantedSettings} implementation backed by {@link Settings} and {@link Messages}.
 */
public class GanglandWantedSettings implements WantedSettings {

	@Override
	public boolean isTimerMultiplierEnabled() {
		return Settings.isWantedTimerMultiplierEnabled();
	}

	@Override
	public double getTimerMultiplierAmount() {
		return Settings.getWantedTimerMultiplierAmount();
	}

	@Override
	public int getTimerTime() {
		return Settings.getWantedTimerTime();
	}

	@Override
	public BigDecimal getTakeMoneyAmount() {
		return Settings.getWantedTakeMoneyAmount();
	}

	@Override
	public double getTakeMoneyMultiplier() {
		return Settings.getWantedTakeMoneyMultiplier();
	}

	@Override
	public String getWantedDecreasedMessageTemplate() {
		return Messages.WANTED_DECREASED.toString();
	}

	@Override
	public String formatMoneyLoss(BigDecimal amount) {
		return "&c&l-" + Settings.getMoneySymbol() + Settings.formatAmount(amount);
	}
}
