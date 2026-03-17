package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.bounty.BountySettings;
import me.luckyraven.file.configuration.Settings;

/**
 * {@link BountySettings} implementation backed by {@link Settings}.
 */
public class GanglandBountySettings implements BountySettings {

	@Override
	public int getTimeInterval() {
		return Settings.getBountyTimeInterval();
	}

	@Override
	public double getEachKillValue() {
		return Settings.getBountyEachKillValue();
	}

	@Override
	public double getTimerMultiple() {
		return Settings.getBountyTimerMultiple();
	}

	@Override
	public double getTimerMax() {
		return Settings.getBountyTimerMax();
	}
}
