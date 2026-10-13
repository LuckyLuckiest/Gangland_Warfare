package org.luckyraven.gangland.copsncrooks.detainment.economy;

import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;

/**
 * Thin contract exposing detainment cost / timing knobs to cops-n-crooks without a direct import of {@code Settings}
 * from gangland-impl.
 */
public interface DetainmentCostsContract {

	int getTransitDelayTicks();

	double getHandcuffBribeBaseCost();

	double getHandcuffBribePerLevel();

	double getBailBaseCost();

	double getBailPerLevel();

	double getJailBribeBaseCost();

	double getJailBribePerLevel();

	double getJailBribeSuccessChance();

	int getJailBribeFailPenaltySeconds();

	int getSentenceBaseSeconds();

	int getSentencePerWantedLevelSeconds();

	int getBreakFreeTapsRequired();

	int getBreakFreeResetWindowTicks();

	String getFallbackExitWaypoint();

	// 0.21.0: origin multipliers default to 1.0 (never dereferences the origin, so null is safe)
	default double getSentenceMultiplier(ArrestOrigin origin) {
		return 1.0;
	}

	default double getBailMultiplier(ArrestOrigin origin) {
		return 1.0;
	}

	default double getFineMultiplier(ArrestOrigin origin) {
		return 1.0;
	}

	// 0.21.0: lockpick tap weight 1 and snap chance 0.0 keep today's break-free behaviour
	default int getLockpickTapWeight() {
		return 1;
	}

	default double getLockpickSnapChance() {
		return 0.0;
	}

	default double computeHandcuffBribeCost(int wantedLevel) {
		return getHandcuffBribeBaseCost() + Math.max(0, wantedLevel) * getHandcuffBribePerLevel();
	}

	default double computeBailCost(int wantedLevel) {
		return getBailBaseCost() + Math.max(0, wantedLevel) * getBailPerLevel();
	}

	default double computeJailBribeCost(int wantedLevel) {
		return getJailBribeBaseCost() + Math.max(0, wantedLevel) * getJailBribePerLevel();
	}

	default int computeSentenceSeconds(int wantedLevel) {
		return getSentenceBaseSeconds() + Math.max(0, wantedLevel) * getSentencePerWantedLevelSeconds();
	}
}
