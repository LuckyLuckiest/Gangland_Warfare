package org.luckyraven.gangland.turf.listener;

import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins TF-25: disbanding a gang releases the turfs it owned and cancels the captures it was running. The listener
 * never looks the gang up (GangDeleteCommand removes it before the event fires), so only the gang id is used.
 */
@DisplayName("TurfGangDeleteListener - disbanding a gang frees its turfs and cancels its captures (TF-25)")
class TurfGangDeleteListenerTest {

	private TurfManager            turfs;
	private TurfGangDeleteListener listener;

	@BeforeEach
	void setUp() {
		TurfRepositoryContract repository = mock(TurfRepositoryContract.class);
		turfs    = new TurfManager(repository);
		listener = new TurfGangDeleteListener(turfs, new TurfOwnership(turfs));
	}

	private Turf turf(int id, Integer ownerGangId) {
		Turf turf = new Turf(id, "T" + id, new CuboidRegion("world", id * 20, 0, id * 20 + 10, 10), ownerGangId,
		                     BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		return turf;
	}

	@Test
	@DisplayName("disbanding the gang releases its owned turf (GANG_DISBANDED), cancels its contest, leaves an unrelated turf alone")
	void gangDelete_releasesOwnedCancelsContestAndTouchesNothingElse() {
		Turf              owned           = turf(1, 5);
		Turf              contested       = turf(2, 1);
		TurfRuntimeState  contestState    = turfs.getRuntimeState(contested.getId());
		contestState.setState(TurfState.CONTESTING);
		contestState.setChallengerGangId(5);
		Turf              unrelated       = turf(3, 7);
		TurfRuntimeState  unrelatedState  = turfs.getRuntimeState(unrelated.getId());
		unrelatedState.setState(TurfState.CONTESTING);
		unrelatedState.setChallengerGangId(9);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			listener.onGangDelete(new GangDeleteEvent(CaptureFixtures.gang(5)));

			assertNull(owned.getOwnerGangId());
			assertEquals(1, contested.getOwnerGangId());
			assertEquals(TurfState.IDLE, contestState.getState());
			assertEquals(7, unrelated.getOwnerGangId());
			assertEquals(TurfState.CONTESTING, unrelatedState.getState());

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), times(2)).callEvent(captor.capture());
			List<Event> events = captor.getAllValues();
			TurfOwnerChangedEvent changed = (TurfOwnerChangedEvent) events.get(0);
			assertEquals(TurfOwnerChangedEvent.Cause.GANG_DISBANDED, changed.getCause());
			assertEquals(5, changed.getOldOwnerGangId());
			assertNull(changed.getNewOwnerGangId());
			TurfCaptureFailedEvent failed = (TurfCaptureFailedEvent) events.get(1);
			assertEquals(TurfCaptureFailedEvent.Reason.CANCELLED, failed.getReason());
		}
	}
}
