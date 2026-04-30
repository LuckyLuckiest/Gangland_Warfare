package org.luckyraven.gangland.file.configuration.copsncrooks;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.bounty.BountySettings;

import java.math.BigDecimal;

/**
 * {@link BountySettings} implementation backed by {@link Settings}.
 */
public class GanglandBountySettings implements BountySettings {

	@Override
	public int getTimeInterval() {
		return Settings.getBountyTimeInterval();
	}

	@Override
	public BigDecimal getEachKillValue() {
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
