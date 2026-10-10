package org.luckyraven.gangland.turf.listener.powerups;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.support.InMemoryGarrisonRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GarrisonDeployListenerTest {

	private GarrisonManager      garrisons;
	private TurfDefenderDeployer defenders;
	private GarrisonDeployListener listener;

	@BeforeEach
	void setUp() {
		garrisons = new GarrisonManager(new InMemoryGarrisonRepository());
		defenders = mock(TurfDefenderDeployer.class);
		listener = new GarrisonDeployListener(garrisons, defenders, mock(TurfNpcsConfigLoader.class),
		                                      mock(TurfPowerupManager.class), mock(GangLookupContract.class));
	}

	@Test
	@DisplayName("a failed contest returns the surviving guards to the owner's garrison stock")
	void failedContest_givesSurvivingGuardsBackToStock() {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), 1, BigDecimal.TEN, 0L, 0L);
		garrisons.add(1, 0); // stock already consumed at contest start
		when(defenders.aliveCount(1)).thenReturn(2);

		listener.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(2, garrisons.count(1));
		verify(defenders).recall(1);
	}

	@Test
	@DisplayName("a failed contest on an unclaimed turf gives no guards back (no owner to hold stock)")
	void failedContest_unclaimedTurf_addsNothing() {
		Turf turf = new Turf(2, "Turf", new CuboidRegion("world", 0, 0, 10, 10), null, BigDecimal.TEN, 0L, 0L);
		when(defenders.aliveCount(2)).thenReturn(2);

		listener.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.ABANDONED, 3));

		assertEquals(0, garrisons.count(2));
		verify(defenders).recall(2);
	}
}
