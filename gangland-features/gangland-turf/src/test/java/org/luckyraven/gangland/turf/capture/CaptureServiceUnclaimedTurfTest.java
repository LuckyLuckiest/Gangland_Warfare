package org.luckyraven.gangland.turf.capture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureProgressEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.RecordingTurfSoundContract;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * Proves {@link CaptureService#tick} for <b>unclaimed</b> turfs under the 0.17.0 one-bar rule (W2-R2, TF-12, D5): the
 * same tug-of-war as an owned turf at {@code Capture.Duration_Seconds}, with every other gang's counted members as the
 * opposition. The old CLAIM/CONSOLIDATE phases are gone: the phase stays CLAIM, no handover happens, and the abandon
 * grace still applies.
 */
@DisplayName("CaptureService - unclaimed turf, one bar (W2-R2)")
class CaptureServiceUnclaimedTurfTest {

	private InMemoryTurfRepository     repository;
	private TurfManager                turfManager;
	private FakeGangLookup             gangs;
	private FakeUserLookup             users;
	private RecordingTurfSoundContract sounds;

	private Gang gangA;
	private Gang gangB;
	private Turf turf;

	@BeforeEach
	void setUp() {
		repository  = new InMemoryTurfRepository();
		turfManager = new TurfManager(repository);
		gangs       = new FakeGangLookup();
		users       = new FakeUserLookup();
		sounds      = new RecordingTurfSoundContract();

		gangA = CaptureFixtures.gang(1);
		gangB = CaptureFixtures.gang(2);
		gangs.register(gangA);
		gangs.register(gangB);

		turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), null, BigDecimal.TEN, 0L, 0L);
		turfManager.create(turf);
	}

	/** The one bar runs at {@code durationSeconds}; the abandon grace is {@code abandonGraceSeconds}. */
	private CaptureService service(int durationSeconds, int abandonGraceSeconds) {
		CaptureSettings settings = CaptureFixtures.settings(durationSeconds, 15, abandonGraceSeconds, 10,
		                                                    new int[]{25, 50, 75}, 90, 90);
		return new CaptureService(turfManager, gangs, users, settings, sounds, new TurfOwnership(turfManager));
	}

	private TurfRuntimeState stateAt(double progress, Integer challengerGangId, long lastSeenAt) {
		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setPhase(CapturePhase.CLAIM);
		state.setCaptureProgress(progress);
		state.setChallengerGangId(challengerGangId);
		state.setLastChallengerSeenAt(lastSeenAt);
		return state;
	}

	private final Map<UUID, Player> playersByUuid = new HashMap<>();

	private void insidePlayer(int gangId, Map<UUID, Turf> cache) {
		Player player = CaptureFixtures.player();
		users.register(player, CaptureFixtures.userInGang(gangId));
		cache.put(player.getUniqueId(), turf);
		playersByUuid.put(player.getUniqueId(), player);
	}

	private void tick(CaptureService service, Map<UUID, Turf> cache) {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(playersByUuid.get(id));
			}
			service.tick(cache);
		}
	}

	@Test
	@DisplayName("one bar: the fill is the attackers minus everyone else inside, scaled by Capture.Duration_Seconds")
	void oneBar_fillRateIsNetAgainstOthers() {
		CaptureService service = service(20, 15); // one bar: 100 / 20 = 5 per net point
		stateAt(50.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangA.getId(), cache);
		insidePlayer(gangA.getId(), cache);
		insidePlayer(gangB.getId(), cache);

		tick(service, cache);

		assertEquals(55.0, turfManager.getRuntimeState(turf.getId()).getCaptureProgress(), 1e-9, "2 - 1 = +1 net");
	}

	@Test
	@DisplayName("one bar: the phase stays CLAIM at every tick and no CONSOLIDATE handover ever happens")
	void oneBar_phaseStaysClaimAtEveryTick() {
		CaptureService service = service(20, 15);
		stateAt(0.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangA.getId(), cache);

		for (int i = 0; i < 4; i++) {
			tick(service, cache);
			assertEquals(CapturePhase.CLAIM, turfManager.getRuntimeState(turf.getId()).getPhase());
		}
	}

	@Test
	@DisplayName("one bar: an empty contest freezes (no decay) while inside the abandon-grace window")
	void oneBar_freezesWhileEmptyWithinGrace() {
		CaptureService service = service(20, 30);
		stateAt(40.0, gangA.getId(), System.currentTimeMillis());

		tick(service, new HashMap<>());

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(TurfState.CONTESTING, state.getState());
		assertEquals(40.0, state.getCaptureProgress(), 1e-9, "an empty contest freezes instead of decaying");
	}

	@Test
	@DisplayName("one bar: an empty contest ends ABANDONED once the abandon-grace window has elapsed")
	void oneBar_cancelsAbandonedAfterGraceExpires() {
		CaptureService service = service(20, 15);
		long           longAgo = System.currentTimeMillis() - 16_000L;
		stateAt(40.0, gangA.getId(), longAgo);

		tick(service, new HashMap<>());

		assertEquals(TurfState.IDLE, turfManager.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("one bar: reaching 100 completes the capture and hands the turf to the challenger")
	void oneBar_completesAndTransfersOwnership() {
		CaptureService service = service(20, 15); // base = 5
		stateAt(95.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangA.getId(), cache);
		insidePlayer(gangA.getId(), cache);

		tick(service, cache);

		assertEquals(gangA.getId(), turf.getOwnerGangId());
		assertEquals(TurfState.COOLDOWN, turfManager.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("one bar: milestone events report the claim bar only, with consolidate pinned at 0")
	void milestoneEvents_reportClaimOnly() {
		CaptureService service = service(4, 15); // base = 25
		stateAt(0.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangA.getId(), cache);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(playersByUuid.get(id));
			}
			service.tick(cache);

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager()).callEvent(captor.capture());
			TurfCaptureProgressEvent event = (TurfCaptureProgressEvent) captor.getValue();
			assertEquals(CapturePhase.CLAIM, event.getPhase());
			assertEquals(25.0, event.getClaimProgress());
			assertEquals(0.0, event.getConsolidateProgress());
		}
	}
}
