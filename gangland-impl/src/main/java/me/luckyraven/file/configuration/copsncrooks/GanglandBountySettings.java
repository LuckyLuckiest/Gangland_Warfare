package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.bounty.BountySettings;
import me.luckyraven.file.configuration.SettingAddon;

/**
 * {@link BountySettings} implementation backed by {@link SettingAddon}.
 */
public class GanglandBountySettings implements BountySettings {

	@Override
	public int getTimeInterval() {
		return SettingAddon.getBountyTimeInterval();
	}

	@Override
	public double getEachKillValue() {
		return SettingAddon.getBountyEachKillValue();
	}

	@Override
	public double getTimerMultiple() {
		return SettingAddon.getBountyTimerMultiple();
	}

	@Override
	public double getTimerMax() {
		return SettingAddon.getBountyTimerMax();
	}
}
