package org.luckyraven.gangland.copsncrooks.detainment.economy;

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
