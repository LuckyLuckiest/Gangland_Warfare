package org.luckyraven.gangland.copsncrooks.integration.detainment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.detainment.settings.SurrenderSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 0.21.0 fnd-origin (P-ORIGIN-3): only SURRENDERED reads the Surrender multipliers; CUFFED, BUSTED_DOWNED and
 * DEAD_OR_ALIVE price at 1.0 so every 0.19 price is unchanged.
 */
@DisplayName("GanglandDetainmentCosts - origin multipliers from detainment.yml")
class GanglandDetainmentCostsTest {

	private DetainmentSettings        settings;
	private GanglandDetainmentCosts   costs;

	@BeforeEach
	void setUp() {
		settings = mock(DetainmentSettings.class);
		// deliberately three different numbers so a crossed wire shows
		when(settings.getSurrender()).thenReturn(new SurrenderSettings(true, 3, 0.4, 0.3, 0.2, 10));
		when(settings.getLockpickTapWeight()).thenReturn(5);
		when(settings.getLockpickSnapChance()).thenReturn(0.25);
		costs = new GanglandDetainmentCosts(settings);
	}

	@Test
	@DisplayName("SURRENDERED uses the sentence, fine and bail knobs of Detainment.Surrender, each its own")
	void surrenderedUsesSurrenderKnobs() {
		assertEquals(0.4, costs.getSentenceMultiplier(ArrestOrigin.SURRENDERED));
		assertEquals(0.3, costs.getFineMultiplier(ArrestOrigin.SURRENDERED));
		assertEquals(0.2, costs.getBailMultiplier(ArrestOrigin.SURRENDERED));
	}

	@Test
	@DisplayName("every other origin, and a null origin, prices at 1.0")
	void otherOrigins_areOne() {
		for (ArrestOrigin origin : new ArrestOrigin[]{ArrestOrigin.CUFFED, ArrestOrigin.BUSTED_DOWNED,
		                                              ArrestOrigin.DEAD_OR_ALIVE, null}) {
			assertEquals(1.0, costs.getSentenceMultiplier(origin), String.valueOf(origin));
			assertEquals(1.0, costs.getFineMultiplier(origin), String.valueOf(origin));
			assertEquals(1.0, costs.getBailMultiplier(origin), String.valueOf(origin));
		}
	}

	@Test
	@DisplayName("the lockpick getters read Break_Free.Lockpick")
	void lockpickGetters_delegate() {
		assertEquals(5, costs.getLockpickTapWeight());
		assertEquals(0.25, costs.getLockpickSnapChance());
	}

	@Test
	@DisplayName("a reload is seen live: the multipliers are read from settings on every call")
	void multipliers_followTheSettingsLive() {
		when(settings.getSurrender()).thenReturn(new SurrenderSettings(true, 3, 0.9, 0.8, 0.7, 10));

		assertEquals(0.9, costs.getSentenceMultiplier(ArrestOrigin.SURRENDERED));
		assertEquals(0.7, costs.getBailMultiplier(ArrestOrigin.SURRENDERED));
	}
}
