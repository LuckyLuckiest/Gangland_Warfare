package org.luckyraven.gangland.turf.capture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.RecordingTurfSoundContract;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TF-27: runtime state is not persisted, so a turf captured just before a restart loads as IDLE although its
 * persisted cooldown still runs. After the first tick the displayed state must say COOLDOWN while the cooldown runs,
 * and stay IDLE when there is no recent capture or the cooldown already elapsed.
 */
@DisplayName("CaptureService - cooldown derived from the persisted timestamp after a restart (TF-27)")
class CaptureServiceCooldownOnLoadTest {

	private TurfManager     turfs;
	private CaptureService  service;
	private FakeGangLookup  gangs;

	@BeforeEach
	void setUp() {
		gangs = new FakeGangLookup();
		gangs.register(CaptureFixtures.gang(7));
		turfs = new TurfManager(new InMemoryTurfRepository());
		CaptureSettings settings = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		service = new CaptureService(turfs, gangs, new FakeUserLookup(), settings, new RecordingTurfSoundContract(),
		                             new TurfOwnership(turfs));
	}

	private Turf registerOwnedTurf(long lastCaptureTimestamp) {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), 7, BigDecimal.TEN, 0L,
		                     lastCaptureTimestamp);
		turfs.create(turf);
		return turf;
	}

	@Test
	@DisplayName("a turf captured 1 minute ago is COOLDOWN after the first tick")
	void recentCapture_isCooldownAfterFirstTick() {
		Turf turf = registerOwnedTurf(System.currentTimeMillis() - 60_000L);

		service.tick(Map.of());

		assertEquals(TurfState.COOLDOWN, turfs.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("a turf never captured stays IDLE after the first tick")
	void noCapture_staysIdle() {
		Turf turf = registerOwnedTurf(0L);

		service.tick(Map.of());

		assertEquals(TurfState.IDLE, turfs.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("a turf whose cooldown elapsed 1 minute ago stays IDLE after the first tick")
	void elapsedCooldown_staysIdle() {
		Turf turf = registerOwnedTurf(System.currentTimeMillis() - 16 * 60_000L);

		service.tick(Map.of());

		assertEquals(TurfState.IDLE, turfs.getRuntimeState(turf.getId()).getState());
	}
}
