package org.luckyraven.gangland.turf.capture;

import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pins the W0 ownership contract: {@link TurfOwnership} is the only writer of a turf's owner outside the repository
 * load, every real owner change fires exactly one {@link TurfOwnerChangedEvent}, and a non-capture change cancels a
 * running capture first (TF-01, TF-04, TF-10, TF-25, TF-28, TF-40).
 */
@DisplayName("TurfOwnership - the single owner writer (W0 ownership contract)")
class TurfOwnershipTest {

	private TurfRepositoryContract repository;
	private TurfManager            turfs;
	private TurfOwnership          ownership;

	@BeforeEach
	void setUp() {
		repository = mock(TurfRepositoryContract.class);
		turfs      = new TurfManager(repository);
		ownership  = new TurfOwnership(turfs);
	}

	private Turf turf(Integer ownerGangId) {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), ownerGangId, BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		clearInvocations(repository);
		return turf;
	}

	private static List<Event> firedEvents(BukkitStatics bukkit, int expected) {
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(bukkit.pluginManager(), times(expected)).callEvent(captor.capture());
		return captor.getAllValues();
	}

	@Test
	@DisplayName("a changed owner is written, persisted and announced by exactly one event carrying old, new and cause")
	void setOwner_changedOwner_persistsAndFiresExactlyOneEvent() {
		Turf turf = turf(1);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertTrue(ownership.setOwner(turf, 2, TurfOwnerChangedEvent.Cause.ADMIN));

			assertEquals(2, turf.getOwnerGangId());
			verify(repository).save(turf);
			TurfOwnerChangedEvent changed = (TurfOwnerChangedEvent) firedEvents(bukkit, 1).get(0);
			assertEquals(1, changed.getOldOwnerGangId());
			assertEquals(2, changed.getNewOwnerGangId());
			assertEquals(TurfOwnerChangedEvent.Cause.ADMIN, changed.getCause());
		}
	}

	@Test
	@DisplayName("setting the owner it already has writes nothing and fires nothing")
	void setOwner_sameOwner_firesNothing() {
		Turf turf = turf(1);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertFalse(ownership.setOwner(turf, 1, TurfOwnerChangedEvent.Cause.ADMIN));

			verify(repository, never()).save(turf);
			verify(bukkit.pluginManager(), never()).callEvent(any(Event.class));
		}
	}

	@Test
	@DisplayName("an admin owner change during a contest fires TurfCaptureFailedEvent(CANCELLED) before TurfOwnerChangedEvent")
	void setOwner_duringContest_firesCancelledThenOwnerChanged() {
		Turf              turf  = turf(1);
		TurfRuntimeState  state = turfs.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(2);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			ownership.setOwner(turf, null, TurfOwnerChangedEvent.Cause.ADMIN);

			assertEquals(TurfState.IDLE, state.getState());
			List<Event> events = firedEvents(bukkit, 2);
			TurfCaptureFailedEvent failed = (TurfCaptureFailedEvent) events.get(0);
			assertEquals(TurfCaptureFailedEvent.Reason.CANCELLED, failed.getReason());
			TurfOwnerChangedEvent changed = (TurfOwnerChangedEvent) events.get(1);
			assertNull(changed.getNewOwnerGangId());
		}
	}

	@Test
	@DisplayName("a CAPTURE-caused owner change never cancels the capture that is completing it")
	void setOwner_captureCause_neverCancels() {
		Turf             turf  = turf(1);
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(2);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			ownership.setOwner(turf, 2, TurfOwnerChangedEvent.Cause.CAPTURE);

			assertEquals(TurfState.CONTESTING, state.getState());
			firedEvents(bukkit, 1);
		}
	}

	@Test
	@DisplayName("cancelContest carries the challenger gang id on the failed event (W2-N1 tells both sides)")
	void cancelContest_carriesChallenger() {
		Turf             turf  = turf(1);
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(5);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertTrue(ownership.cancelContest(turf, TurfCaptureFailedEvent.Reason.CANCELLED));

			TurfCaptureFailedEvent failed = (TurfCaptureFailedEvent) firedEvents(bukkit, 1).get(0);
			assertEquals(5, failed.getChallengerGangId());
		}
	}

	@Test
	@DisplayName("cancelContest on an idle turf returns false and fires nothing")
	void cancelContest_idle_returnsFalseAndFiresNothing() {
		Turf turf = turf(1);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertFalse(ownership.cancelContest(turf, TurfCaptureFailedEvent.Reason.CANCELLED));

			verify(bukkit.pluginManager(), never()).callEvent(any(Event.class));
		}
	}

	@Test
	@DisplayName("cancelContest on a contesting turf resets it to IDLE and fires exactly one failed event")
	void cancelContest_contesting_resetsAndFiresOnce() {
		Turf             turf  = turf(1);
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(2);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertTrue(ownership.cancelContest(turf, TurfCaptureFailedEvent.Reason.CANCELLED));

			assertEquals(TurfState.IDLE, state.getState());
			TurfCaptureFailedEvent failed = (TurfCaptureFailedEvent) firedEvents(bukkit, 1).get(0);
			assertEquals(TurfCaptureFailedEvent.Reason.CANCELLED, failed.getReason());
		}
	}
}
