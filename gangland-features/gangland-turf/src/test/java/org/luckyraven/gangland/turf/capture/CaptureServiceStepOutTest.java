package org.luckyraven.gangland.turf.capture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.RecordingTurfSoundContract;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T-252: the abandon grace counts any attacker of the challenger gang inside the turf, counted or still counting in.
 * Owner call (defaulted): the grace runs only while nobody of the attacking gang is inside.
 */
@DisplayName("CaptureService - stepping out of a contested turf (T-252)")
class CaptureServiceStepOutTest {

	private static final long   T0       = 100_000_000L;
	private static final CaptureRules RULES = new CaptureRules(CaptureRules.Window.OWNERS_ONLINE, 0, 0, 15, 0, 0, 4);

	private InMemoryTurfRepository     repository;
	private TurfManager                turfManager;
	private FakeGangLookup             gangs;
	private FakeUserLookup             users;
	private RecordingTurfSoundContract sounds;
	private Gang                       owner;
	private Gang                       challenger;
	private Turf                       turf;
	private CaptureService             service;
	private final Map<UUID, Player>    playersByUuid = new HashMap<>();
	private Player                     attacker;

	@BeforeEach
	void setUp() {
		repository  = new InMemoryTurfRepository();
		turfManager = new TurfManager(repository);
		gangs       = new FakeGangLookup();
		users       = new FakeUserLookup();
		sounds      = new RecordingTurfSoundContract();

		owner      = CaptureFixtures.gang(1);
		challenger = CaptureFixtures.gang(2);
		gangs.register(owner);
		gangs.register(challenger);

		turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), owner.getId(), BigDecimal.TEN, 0L, 0L);
		turfManager.create(turf);

		// 600 s capture: a single attacker moves the bar 1/6 per tick, so the bar cannot finish inside the test.
		CaptureSettings settings = CaptureFixtures.settings(600, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		service = new CaptureService(turfManager, gangs, users, settings, sounds, new TurfOwnership(turfManager),
		                             new CaptureEligibility(gangs, settings), () -> RULES, turfId -> 0);

		attacker = CaptureFixtures.player();
		users.register(attacker, CaptureFixtures.userInGang(challenger.getId()));
		playersByUuid.put(attacker.getUniqueId(), attacker);

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(challenger.getId());
		state.setCaptureProgress(40.0);
		state.setLastChallengerSeenAt(T0 - 16_000L);
	}

	/** One 1-Hz tick; the attacker is in the turf only when {@code inside} is true. */
	private void tick(boolean inside, long now) {
		Map<UUID, Turf> cache = new HashMap<>();
		if (inside) {
			cache.put(attacker.getUniqueId(), turf);
		}
		service.tick(cache, now);
	}

	@Test
	@DisplayName("oneTickStepOut_keepsTheContest: a one-tick step out and back in does not abandon the contest")
	void oneTickStepOut_keepsTheContest() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getPlayer(any(UUID.class)))
			      .thenAnswer(invocation -> playersByUuid.get(invocation.getArgument(0)));
			// Inside for the 16 s before T0 (counted), out at T0 only, back in from T0 + 1 s.
			for (long t = T0 - 16_000L; t < T0; t += 1_000L) {
				tick(true, t);
			}
			tick(false, T0);
			for (long t = T0 + 1_000L; t <= T0 + 20_000L; t += 1_000L) {
				tick(true, t);
			}

			TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
			assertEquals(TurfState.CONTESTING, state.getState());
			verify(bukkit.pluginManager(), never()).callEvent(any(TurfCaptureFailedEvent.class));
		}
	}

	@Test
	@DisplayName("realDeparture_endsAbandonedAfterGrace: an attacker who stays out ends the contest after the grace")
	void realDeparture_endsAbandonedAfterGrace() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getPlayer(any(UUID.class)))
			      .thenAnswer(invocation -> playersByUuid.get(invocation.getArgument(0)));
			// Inside until T0, then gone for good.
			for (long t = T0 - 16_000L; t <= T0; t += 1_000L) {
				tick(true, t);
			}
			for (long t = T0 + 1_000L; t <= T0 + 14_000L; t += 1_000L) {
				tick(false, t);
			}
			TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
			assertEquals(TurfState.CONTESTING, state.getState(), "still inside the 15 s grace at T0 + 14 s");

			tick(false, T0 + 16_000L);

			assertEquals(TurfState.IDLE, state.getState());
			verify(bukkit.pluginManager()).callEvent(any(TurfCaptureFailedEvent.class));
		}
	}
}
