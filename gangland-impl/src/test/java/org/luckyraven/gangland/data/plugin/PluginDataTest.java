package org.luckyraven.gangland.data.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic pin for {@link PluginData}: scan-due boundary maths, post-scan date advancement, and the shared
 * static id counter's reuse hazard (Observation #11, core-lifecycle.md — see also {@link PluginManagerTest}, which
 * exercises the same counter through {@code PluginManager.initialize()}'s reload path end to end).
 *
 * <p>{@code PluginData.ID} is a process-wide static counter; every test resets it to {@code 0} first so this class
 * is independent of execution order relative to other test classes in this module.
 */
@DisplayName("PluginData")
class PluginDataTest {

	@BeforeEach
	void resetIdCounter() {
		PluginData.setID(0);
	}

	@Test
	@DisplayName("isScanDue is false before the scheduled date and true once it has passed")
	void isScanDue_reflectsScheduledDate() {
		long now = System.currentTimeMillis();

		PluginData due    = new PluginData(1, now, now, now - 1_000);
		PluginData notDue = new PluginData(2, now, now, now + 60_000);

		assertTrue(due.isScanDue());
		assertFalse(notDue.isScanDue());
	}

	@Test
	@DisplayName("isScanDue uses >=, so the exact scheduled instant already counts as due")
	void isScanDue_trueAtExactBoundary() {
		long       now  = System.currentTimeMillis();
		PluginData data = new PluginData(1, now, now, now);

		assertTrue(data.isScanDue(), "System.currentTimeMillis() only moves forward, so 'now' at construction " +
				"is always <= 'now' when isScanDue() reads the clock again");
	}

	@Test
	@DisplayName("updateAfterScan sets scanDate to now and reschedules N days out from that new scanDate")
	void updateAfterScan_advancesBothDates() {
		PluginData data   = new PluginData(1, 0L, 0L, 0L);
		long       before = System.currentTimeMillis();

		data.updateAfterScan(30);

		long before2 = System.currentTimeMillis();
		assertTrue(data.getScanDate() >= before && data.getScanDate() <= before2);

		long expectedScheduled = data.getScanDate() + Duration.ofDays(30).toMillis();
		assertTrue(Math.abs(data.getScheduledScanDate() - expectedScheduled) < 5_000,
				"scheduled scan date should land ~30 days after the freshly-set scanDate");
	}

	@Test
	@DisplayName("createInitial starts scanDate at zero and schedules the first scan N days from now")
	void createInitial_schedulesFirstScanFromZeroScanDate() {
		PluginData data = PluginData.createInitial(7);

		assertEquals(0L, data.getScanDate());

		long expected = Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli();
		assertTrue(Math.abs(data.getScheduledScanDate() - expected) < 5_000);
	}

	@Test
	@DisplayName("getNewId increments across successive constructions when starting from a fresh counter")
	void getNewId_incrementsMonotonically() {
		PluginData first  = new PluginData(0L, 0L, 0L);
		PluginData second = new PluginData(0L, 0L, 0L);

		assertEquals(0, first.getId());
		assertEquals(1, second.getId());
	}

	@Test
	@DisplayName("Observation #11 (core-lifecycle.md): seeding the counter from an already-used id hands that " +
			"same id to the very next freshly-created PluginData")
	void newPluginData_reusesASeededId_pinningObservation11() {
		// Mirrors what PluginManager.initialize() does for a single loaded row: PluginData.setID(loadedRow.getId()).
		PluginData.setID(5);

		PluginData created = PluginData.createInitial(30);

		assertEquals(5, created.getId(),
				"current behaviour: getNewId() returns ID *then* increments, so seeding the counter with an " +
						"already-assigned id (5) immediately hands that same id out again instead of the next " +
						"free one (6)");
	}
}
