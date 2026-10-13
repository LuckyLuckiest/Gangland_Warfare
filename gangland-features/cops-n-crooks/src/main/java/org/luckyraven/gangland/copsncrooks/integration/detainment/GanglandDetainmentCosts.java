package org.luckyraven.gangland.copsncrooks.integration.detainment;

import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;

/**
 * Delegates every detainment cost / timing knob to {@link DetainmentSettings} ({@code copsncrooks/detainment.yml}).
 * 0.21.0: only {@link ArrestOrigin#SURRENDERED} reads the {@code Surrender} multipliers; every other origin prices at
 * 1.0 so existing prices do not move.
 */
public final class GanglandDetainmentCosts implements DetainmentCostsContract {

	private final DetainmentSettings settings;

	public GanglandDetainmentCosts(DetainmentSettings settings) {
		this.settings = settings;
	}

	@Override
	public int getTransitDelayTicks() {
		return settings.getTransitDelayTicks();
	}

	@Override
	public double getHandcuffBribeBaseCost() {
		return settings.getHandcuffBribeBaseCost();
	}

	@Override
	public double getHandcuffBribePerLevel() {
		return settings.getHandcuffBribePerLevel();
	}

	@Override
	public double getBailBaseCost() {
		return settings.getBailBaseCost();
	}

	@Override
	public double getBailPerLevel() {
		return settings.getBailPerLevel();
	}

	@Override
	public double getJailBribeBaseCost() {
		return settings.getJailBribeBaseCost();
	}

	@Override
	public double getJailBribePerLevel() {
		return settings.getJailBribePerLevel();
	}

	@Override
	public double getJailBribeSuccessChance() {
		return settings.getJailBribeSuccessChance();
	}

	@Override
	public int getJailBribeFailPenaltySeconds() {
		return settings.getJailBribeFailPenaltySeconds();
	}

	@Override
	public int getSentenceBaseSeconds() {
		return settings.getSentenceBaseSeconds();
	}

	@Override
	public int getSentencePerWantedLevelSeconds() {
		return settings.getSentencePerWantedLevelSeconds();
	}

	@Override
	public int getBreakFreeTapsRequired() {
		return settings.getBreakFreeTapsRequired();
	}

	@Override
	public int getBreakFreeResetWindowTicks() {
		return settings.getBreakFreeResetWindowTicks();
	}

	@Override
	public String getFallbackExitWaypoint() {
		return settings.getFallbackExitWaypoint();
	}

	// 0.21.0: read on every call so a reload of detainment.yml is seen live
	@Override
	public double getSentenceMultiplier(ArrestOrigin origin) {
		return origin == ArrestOrigin.SURRENDERED ? settings.getSurrender().sentenceMultiplier() : 1.0;
	}

	@Override
	public double getBailMultiplier(ArrestOrigin origin) {
		return origin == ArrestOrigin.SURRENDERED ? settings.getSurrender().bailMultiplier() : 1.0;
	}

	@Override
	public double getFineMultiplier(ArrestOrigin origin) {
		return origin == ArrestOrigin.SURRENDERED ? settings.getSurrender().fineMultiplier() : 1.0;
	}

	@Override
	public int getLockpickTapWeight() {
		return settings.getLockpickTapWeight();
	}

	@Override
	public double getLockpickSnapChance() {
		return settings.getLockpickSnapChance();
	}
}
