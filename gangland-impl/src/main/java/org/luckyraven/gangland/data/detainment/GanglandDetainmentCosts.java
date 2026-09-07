package org.luckyraven.gangland.data.detainment;

import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.file.configuration.Settings;

/**
 * Delegates every detainment cost / timing knob to the static {@link Settings} fields populated from settings.yml.
 */
public final class GanglandDetainmentCosts implements DetainmentCostsContract {

	@Override
	public int getTransitDelayTicks() {
		return Settings.getDetainmentTransitDelayTicks();
	}

	@Override
	public double getHandcuffBribeBaseCost() {
		return Settings.getDetainmentHandcuffBribeBaseCost();
	}

	@Override
	public double getHandcuffBribePerLevel() {
		return Settings.getDetainmentHandcuffBribePerLevel();
	}

	@Override
	public double getBailBaseCost() {
		return Settings.getDetainmentBailBaseCost();
	}

	@Override
	public double getBailPerLevel() {
		return Settings.getDetainmentBailPerLevel();
	}

	@Override
	public double getJailBribeBaseCost() {
		return Settings.getDetainmentJailBribeBaseCost();
	}

	@Override
	public double getJailBribePerLevel() {
		return Settings.getDetainmentJailBribePerLevel();
	}

	@Override
	public double getJailBribeSuccessChance() {
		return Settings.getDetainmentJailBribeSuccessChance();
	}

	@Override
	public int getJailBribeFailPenaltySeconds() {
		return Settings.getDetainmentJailBribeFailPenaltySeconds();
	}

	@Override
	public int getSentenceBaseSeconds() {
		return Settings.getDetainmentSentenceBaseSeconds();
	}

	@Override
	public int getSentencePerWantedLevelSeconds() {
		return Settings.getDetainmentSentencePerWantedLevelSeconds();
	}

	@Override
	public int getBreakFreeTapsRequired() {
		return Settings.getDetainmentBreakFreeTapsRequired();
	}

	@Override
	public int getBreakFreeResetWindowTicks() {
		return Settings.getDetainmentBreakFreeResetWindowTicks();
	}

	@Override
	public String getFallbackExitWaypoint() {
		return Settings.getDetainmentFallbackExitWaypoint();
	}
}
