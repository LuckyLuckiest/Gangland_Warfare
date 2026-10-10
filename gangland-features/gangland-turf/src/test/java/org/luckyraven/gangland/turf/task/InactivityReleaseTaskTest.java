package org.luckyraven.gangland.turf.task;

import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pins TF-10 for the daily sweep: every owner it clears fires one {@link TurfOwnerChangedEvent} with the matching cause.
 */
@DisplayName("InactivityReleaseTask - daily sweep fires TurfOwnerChangedEvent per release (TF-10)")
class InactivityReleaseTaskTest {

	private static final int DAYS = 7;

	private TurfManager        turfs;
	private FakeGangLookup     gangs;
	private InactivityReleaseTask task;

	@BeforeEach
	void setUp() {
		TurfRepositoryContract repository = mock(TurfRepositoryContract.class);
		turfs = new TurfManager(repository);
		gangs = new FakeGangLookup();
		task  = new InactivityReleaseTask(turfs, gangs, new TurfOwnership(turfs), DAYS);
	}

	private Turf turf(int id, int ownerGangId) {
		Turf turf = new Turf(id, "T" + id, new CuboidRegion("world", id * 20, 0, id * 20 + 10, 10), ownerGangId,
		                     BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		return turf;
	}

	private void gang(int id, long lastMemberOnlineAt) {
		Gang gang = CaptureFixtures.gang(id);
		when(gang.getLastMemberOnlineAt()).thenReturn(lastMemberOnlineAt);
		gangs.register(gang);
	}

	private static TurfOwnerChangedEvent onlyEvent(BukkitStatics bukkit) {
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(bukkit.pluginManager(), times(1)).callEvent(captor.capture());
		return (TurfOwnerChangedEvent) captor.getValue();
	}

	@Test
	@DisplayName("owner gang no longer exists -> one event with cause ORPHANED")
	void missingGang_firesOrphaned() {
		Turf turf = turf(1, 99);
		gang(7, System.currentTimeMillis()); // another gang is loaded, so the T-196 guard lets the sweep run
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			task.run();

			assertNull(turf.getOwnerGangId());
			TurfOwnerChangedEvent changed = onlyEvent(bukkit);
			assertEquals(TurfOwnerChangedEvent.Cause.ORPHANED, changed.getCause());
			assertEquals(99, changed.getOldOwnerGangId());
		}
	}

	@Test
	@DisplayName("owner gang offline past the threshold -> one event with cause INACTIVITY")
	void inactiveGang_firesInactivity() {
		long tenDaysAgo = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000;
		Turf turf       = turf(2, 2);
		gang(2, tenDaysAgo);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			task.run();

			assertNull(turf.getOwnerGangId());
			assertEquals(TurfOwnerChangedEvent.Cause.INACTIVITY, onlyEvent(bukkit).getCause());
		}
	}

	@Test
	@DisplayName("active owner gang -> no owner change and no event")
	void activeGang_firesNothing() {
		Turf turf = turf(3, 3);
		gang(3, System.currentTimeMillis());
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			task.run();

			assertEquals(3, turf.getOwnerGangId());
			verify(bukkit.pluginManager(), never()).callEvent(any(Event.class));
		}
	}
}
