package org.luckyraven.gangland.turf.capture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureProgressEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.RecordingTurfSoundContract;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * Proves {@link CaptureService#tick} for <b>owned</b> turfs (single-phase weighted head-count tug-of-war), driven
 * entirely through the public {@code tick(Map)} entry point against fake repositories/contracts — matches the
 * "CaptureService maths" bullet in turf.md's Test Surface section.
 *
 * <p>{@link #emptyTurf_freezesDuringGraceThenAbandons()} (W2-R2, TF-12) replaces the old Observation #12 pin: an empty
 * contest freezes its bar and ends ABANDONED once Abandon_Grace_Seconds has passed since an attacker was last counted.
 *
 * <p>{@link #thirdGangInside_isIgnoredEntirely()} pins Observation #26: only the registered challenger gang's
 * members count on an owned turf; a third gang standing in the same turf neither helps nor hinders.
 */
@DisplayName("CaptureService — owned turf, single-phase capture")
class CaptureServiceOwnedTurfTest {

	private InMemoryTurfRepository     repository;
	private TurfManager                turfManager;
	private FakeGangLookup             gangs;
	private FakeUserLookup             users;
	private RecordingTurfSoundContract sounds;

	private Gang owner;
	private Gang challenger;
	private Turf turf;

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

		turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), owner.getId(), BigDecimal.TEN,
		                0L, 0L);
		turfManager.create(turf);
	}

	private CaptureService service(int durationSeconds) {
		CaptureSettings settings = CaptureFixtures.settings(durationSeconds, 15, 15, 10,
		                                                    new int[]{25, 50, 75}, 90, 90);
		return new CaptureService(turfManager, gangs, users, settings, sounds, new TurfOwnership(turfManager));
	}

	private TurfRuntimeState contestAt(double progress) {
		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(challenger.getId());
		state.setCaptureProgress(progress);
		return state;
	}

	// Tracks every player added via insidePlayerTracked so tick()/the try-blocks can resolve Bukkit.getPlayer(uuid).
	private final Map<UUID, Player> playersByUuid = new HashMap<>();

	private Player playerFor(Map<UUID, Turf> cache, UUID id) {
		return playersByUuid.get(id);
	}

	private Player insidePlayerTracked(int gangId, Map<UUID, Turf> cache) {
		Player player = CaptureFixtures.player();
		users.register(player, CaptureFixtures.userInGang(gangId));
		cache.put(player.getUniqueId(), turf);
		playersByUuid.put(player.getUniqueId(), player);
		return player;
	}

	private void tick(CaptureService service, Map<UUID, Turf> cache) {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				Player player = playerFor(cache, id);
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
			}
			service.tick(cache);
		}
	}

	@ParameterizedTest(name = "{0} attacker(s) vs {1} defender(s) -> net {2}")
	@DisplayName("progress delta = net(attackers - defenders) * base, scaling linearly with head-count")
	@CsvSource({
			"1,0,1",
			"2,0,2",
			"2,2,0",
			"2,3,-1"
	})
	void netArithmetic_scalesProgressByHeadcountDifference(int attackers, int defenders, int expectedNet) {
		CaptureService service = service(20); // base = 100/20 = 5 progress per net point
		contestAt(50.0);

		Map<UUID, Turf> cache = new HashMap<>();
		for (int i = 0; i < attackers; i++) {
			insidePlayerTracked(challenger.getId(), cache);
		}
		for (int i = 0; i < defenders; i++) {
			insidePlayerTracked(owner.getId(), cache);
		}

		tick(service, cache);

		double expected = 50.0 + expectedNet * 5.0;
		assertEquals(expected, turfManager.getRuntimeState(turf.getId()).getCaptureProgress(), 1e-9);
	}

	@Test
	@DisplayName("progress clamps at 100 and completes the capture in the same tick it overshoots")
	void progressClamp_atOneHundredCompletesCapture() {
		CaptureService service = service(1); // base = 100
		contestAt(50.0);
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayerTracked(challenger.getId(), cache);

		tick(service, cache);

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(TurfState.COOLDOWN, state.getState());
		assertEquals(0.0, state.getCaptureProgress());
		assertEquals(challenger.getId(), turf.getOwnerGangId());
	}

	@Test
	@DisplayName("a huge negative delta clamps at 0 rather than going negative (and ends the contest)")
	void progressClamp_atZeroDoesNotGoNegative() {
		CaptureService service = service(1); // base = 100, so 5 defenders vs 0 challengers overshoots by 490
		contestAt(10.0);
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayerTracked(owner.getId(), cache);
		insidePlayerTracked(owner.getId(), cache);
		insidePlayerTracked(owner.getId(), cache);
		insidePlayerTracked(owner.getId(), cache);
		insidePlayerTracked(owner.getId(), cache);

		tick(service, cache);

		assertEquals(0.0, turfManager.getRuntimeState(turf.getId()).getCaptureProgress());
	}

	@Test
	@DisplayName("TF-12: an empty owned contest freezes during Abandon_Grace_Seconds, then ends ABANDONED")
	void emptyTurf_freezesDuringGraceThenAbandons() {
		long            t0       = 100_000_000L;
		CaptureSettings settings = CaptureFixtures.settings(20, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		CaptureService  service  = new CaptureService(turfManager, gangs, users, settings, sounds,
		                                              new TurfOwnership(turfManager));
		TurfRuntimeState state = contestAt(40.0);
		state.setLastChallengerSeenAt(t0);

		service.tick(new HashMap<>(), t0 + 10_000L);
		assertEquals(40.0, state.getCaptureProgress(), 1e-9, "nobody inside: the bar freezes");
		assertEquals(TurfState.CONTESTING, state.getState());

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			service.tick(new HashMap<>(), t0 + 16_000L);
			verify(bukkit.pluginManager()).callEvent(org.mockito.ArgumentMatchers.isA(TurfCaptureFailedEvent.class));
		}
		assertEquals(TurfState.IDLE, state.getState(), "past the 15 s grace the contest ends ABANDONED");
	}

	@Test
	@DisplayName("challengers == 0 always cancels ABANDONED, even when defenders are present")
	void abandoned_winsOverDefendedWheneverNoChallengersRemain() {
		CaptureService service = service(1); // base = 100
		contestAt(10.0);
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayerTracked(owner.getId(), cache); // defenders only, zero challengers present

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				Player player = playerFor(cache, id);
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
			}
			service.tick(cache);

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager()).callEvent(captor.capture());
			assertInstanceOf(TurfCaptureFailedEvent.class, captor.getValue());
			assertEquals(TurfCaptureFailedEvent.Reason.ABANDONED,
			            ((TurfCaptureFailedEvent) captor.getValue()).getReason());
		}
	}

	@Test
	@DisplayName("a DEFENDED cancel carries the challenger gang id on the failed event")
	void defendedCancel_carriesChallenger() {
		CaptureService service = service(1); // base = 100
		contestAt(10.0);
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayerTracked(challenger.getId(), cache); // 1 challenger present
		for (int i = 0; i < 5; i++) {
			insidePlayerTracked(owner.getId(), cache); // 5 defenders
		}

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				Player player = playerFor(cache, id);
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
			}
			service.tick(cache);

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager()).callEvent(captor.capture());
			assertEquals(challenger.getId(), ((TurfCaptureFailedEvent) captor.getValue()).getChallengerGangId());
		}
	}

	@Test
	@DisplayName("defenders outnumbering a present challenger cancels DEFENDED")
	void defended_whenChallengerPresentButOutnumbered() {
		CaptureService service = service(1); // base = 100
		contestAt(10.0);
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayerTracked(challenger.getId(), cache); // 1 challenger present
		for (int i = 0; i < 5; i++) {
			insidePlayerTracked(owner.getId(), cache); // 5 defenders
		}

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				Player player = playerFor(cache, id);
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
			}
			service.tick(cache);

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager()).callEvent(captor.capture());
			assertInstanceOf(TurfCaptureFailedEvent.class, captor.getValue());
			assertEquals(TurfCaptureFailedEvent.Reason.DEFENDED,
			            ((TurfCaptureFailedEvent) captor.getValue()).getReason());
		}
	}

	@Test
	@DisplayName("milestone events fire once per upward crossing, never on a repeat tick that stays above it")
	void milestoneEvents_fireOnlyOnUpwardCrossings() {
		CaptureService service = service(4); // base = 25
		contestAt(0.0);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			// Tick 1: 0 -> 25, crosses the 25 milestone.
			Map<UUID, Turf> cache1 = new HashMap<>();
			insidePlayerTracked(challenger.getId(), cache1);
			for (UUID id : cache1.keySet()) {
				Player player = playerFor(cache1, id);
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
			}
			service.tick(cache1);
			assertEquals(25.0, turfManager.getRuntimeState(turf.getId()).getCaptureProgress());

			// Tick 2: 25 -> 50, crosses the 50 milestone (not a re-fire of 25).
			playersByUuid.clear();
			Map<UUID, Turf> cache2 = new HashMap<>();
			insidePlayerTracked(challenger.getId(), cache2);
			for (UUID id : cache2.keySet()) {
				Player player = playerFor(cache2, id);
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
			}
			service.tick(cache2);
			assertEquals(50.0, turfManager.getRuntimeState(turf.getId()).getCaptureProgress());

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), org.mockito.Mockito.times(2)).callEvent(captor.capture());
			List<Double> firedAt = new ArrayList<>();
			for (Event event : captor.getAllValues()) {
				assertInstanceOf(TurfCaptureProgressEvent.class, event);
				firedAt.add(((TurfCaptureProgressEvent) event).getProgress());
			}
			assertEquals(List.of(25.0, 50.0), firedAt);
		}
	}

	@Test
	@DisplayName("a single tick that overshoots multiple milestones fires one event per milestone crossed")
	void milestoneEvents_multipleCrossingsInOneTickEachFire() {
		CaptureService service = service(1); // base = 100, one attacker jumps 0 -> 100 in one tick
		contestAt(0.0);
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayerTracked(challenger.getId(), cache);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				Player player = playerFor(cache, id);
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
			}
			service.tick(cache);

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			// 3 progress-milestone events (25, 50, 75) plus the TurfOwnerChangedEvent (cause CAPTURE) and the
			// TurfCapturedEvent from completion (W0-A: a capture now fires one owner-changed event).
			verify(bukkit.pluginManager(), org.mockito.Mockito.times(5)).callEvent(captor.capture());
			long progressEvents = captor.getAllValues().stream().filter(TurfCaptureProgressEvent.class::isInstance).count();
			long capturedEvents = captor.getAllValues().stream().filter(TurfCapturedEvent.class::isInstance).count();
			assertEquals(3, progressEvents);
			assertEquals(1, capturedEvents);
		}
	}

	@Test
	@DisplayName("members of an unregistered third gang inside the turf are ignored entirely")
	void thirdGangInside_isIgnoredEntirely() {
		Gang bystanderGang = CaptureFixtures.gang(99);
		gangs.register(bystanderGang);
		CaptureService service = service(20); // base = 5
		contestAt(50.0);

		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayerTracked(challenger.getId(), cache);
		insidePlayerTracked(challenger.getId(), cache);
		insidePlayerTracked(bystanderGang.getId(), cache);
		insidePlayerTracked(bystanderGang.getId(), cache);
		insidePlayerTracked(bystanderGang.getId(), cache);

		tick(service, cache);

		// Only the 2 registered-challenger members count: net = 2, delta = 10. The 3 bystanders are invisible.
		assertEquals(60.0, turfManager.getRuntimeState(turf.getId()).getCaptureProgress(), 1e-9);
	}

	@Test
	@DisplayName("a dead attacker is excluded from the head-count, pausing the capture")
	void deadPlayer_excludedFromClassification() {
		CaptureService service = service(20); // base = 5
		contestAt(50.0).setLastChallengerSeenAt(System.currentTimeMillis());

		Map<UUID, Turf> cache = new HashMap<>();
		Player dead = CaptureFixtures.deadPlayer();
		users.register(dead, CaptureFixtures.userInGang(challenger.getId()));
		cache.put(dead.getUniqueId(), turf);
		playersByUuid.put(dead.getUniqueId(), dead);

		tick(service, cache);

		// Both sides read 0 -> the one bar freezes (TF-12), it does not decay.
		assertEquals(50.0, turfManager.getRuntimeState(turf.getId()).getCaptureProgress(), 1e-9);
	}
}
