package org.luckyraven.gangland.copsncrooks.detainment.economy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the arithmetic in {@link DetainmentCostsContract}'s default methods — the cost/timing knobs consumed by
 * {@code BailService}, {@code BribeService} and {@code SentenceService}. Every formula is
 * {@code base + max(0, wantedLevel) * perLevel}; a negative wanted level must clamp to zero rather than reducing the
 * cost below the base, since none of the callers guard against a negative snapshot themselves (see
 * {@code DetainedPlayer.getWantedAtArrest()}, which can be {@code null}/unset and is defaulted to {@code 0} upstream,
 * but nothing stops a future caller passing a negative value directly).
 */
@DisplayName("DetainmentCostsContract — default cost/timing arithmetic")
class DetainmentCostsContractTest {

	/**
	 * Minimal stub implementing only the abstract knobs; the default methods under test are exercised through it
	 * unmodified.
	 */
	private static final class StubCosts implements DetainmentCostsContract {

		@Override
		public int getTransitDelayTicks() {
			return 100;
		}

		@Override
		public double getHandcuffBribeBaseCost() {
			return 50.0;
		}

		@Override
		public double getHandcuffBribePerLevel() {
			return 10.0;
		}

		@Override
		public double getBailBaseCost() {
			return 25.0;
		}

		@Override
		public double getBailPerLevel() {
			return 5.0;
		}

		@Override
		public double getJailBribeBaseCost() {
			return 15.0;
		}

		@Override
		public double getJailBribePerLevel() {
			return 3.0;
		}

		@Override
		public double getJailBribeSuccessChance() {
			return 0.5;
		}

		@Override
		public int getJailBribeFailPenaltySeconds() {
			return 30;
		}

		@Override
		public int getSentenceBaseSeconds() {
			return 60;
		}

		@Override
		public int getSentencePerWantedLevelSeconds() {
			return 20;
		}

		@Override
		public int getBreakFreeTapsRequired() {
			return 10;
		}

		@Override
		public int getBreakFreeResetWindowTicks() {
			return 20;
		}

		@Override
		public String getFallbackExitWaypoint() {
			return "spawn";
		}
	}

	private final DetainmentCostsContract costs = new StubCosts();

	@Test
	@DisplayName("computeHandcuffBribeCost scales linearly with wanted level")
	void computeHandcuffBribeCost_scalesWithWantedLevel() {
		assertEquals(50.0, costs.computeHandcuffBribeCost(0));
		assertEquals(80.0, costs.computeHandcuffBribeCost(3));
	}

	@Test
	@DisplayName("computeHandcuffBribeCost clamps a negative wanted level to zero extra cost")
	void computeHandcuffBribeCost_negativeLevel_clampsToBase() {
		assertEquals(50.0, costs.computeHandcuffBribeCost(-5));
	}

	@Test
	@DisplayName("computeBailCost scales linearly with wanted level")
	void computeBailCost_scalesWithWantedLevel() {
		assertEquals(25.0, costs.computeBailCost(0));
		assertEquals(45.0, costs.computeBailCost(4));
	}

	@Test
	@DisplayName("computeBailCost clamps a negative wanted level to zero extra cost")
	void computeBailCost_negativeLevel_clampsToBase() {
		assertEquals(25.0, costs.computeBailCost(-1));
	}

	@Test
	@DisplayName("computeJailBribeCost scales linearly with wanted level")
	void computeJailBribeCost_scalesWithWantedLevel() {
		assertEquals(15.0, costs.computeJailBribeCost(0));
		assertEquals(24.0, costs.computeJailBribeCost(3));
	}

	@Test
	@DisplayName("computeSentenceSeconds scales linearly with wanted level")
	void computeSentenceSeconds_scalesWithWantedLevel() {
		assertEquals(60, costs.computeSentenceSeconds(0));
		assertEquals(160, costs.computeSentenceSeconds(5));
	}

	@Test
	@DisplayName("computeSentenceSeconds clamps a negative wanted level to the base duration")
	void computeSentenceSeconds_negativeLevel_clampsToBase() {
		assertEquals(60, costs.computeSentenceSeconds(-3));
	}

}
