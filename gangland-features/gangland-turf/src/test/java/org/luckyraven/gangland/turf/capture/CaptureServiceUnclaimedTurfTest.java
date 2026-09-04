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
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Proves {@link CaptureService#tick} for <b>unclaimed</b> turfs: the two-phase CLAIM/CONSOLIDATE tug-of-war
 * described in the class javadoc and in turf.md's W8. Ties to the "CaptureService maths" bullet in the Test
 * Surface section (CLAIM global fill, CLAIM→CONSOLIDATE election, the CONSOLIDATE→CLAIM(100, null) rollback,
 * and abandon-grace expiry).
 *
 * <p>{@link #claimPhase_fillRateIsFlatRegardlessOfHeadcount()} pins a subtlety implied but not spelled out
 * numerically in turf.md W8 step 3: unlike the owned-turf and CONSOLIDATE tug-of-war, CLAIM's fill delta is a flat
 * {@code base} whenever anyone is inside — it does <b>not</b> scale with how many players (or how many gangs) are
 * present.
 */
@DisplayName("CaptureService — unclaimed turf, two-phase capture")
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

	/** phase1Seconds/phase2Seconds default to 4s each (base 25) unless a test overrides via a fresh service. */
	private CaptureService service(int phase1Seconds, int phase2Seconds, int abandonGraceSeconds) {
		CaptureSettings settings = CaptureFixtures.settings(180, 15, abandonGraceSeconds, 10,
		                                                    new int[]{25, 50, 75}, phase1Seconds, phase2Seconds);
		return new CaptureService(turfManager, gangs, users, settings, sounds);
	}

	private TurfRuntimeState stateAt(CapturePhase phase, double progress, Integer challengerGangId, long lastSeenAt) {
		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setPhase(phase);
		state.setCaptureProgress(progress);
		state.setChallengerGangId(challengerGangId);
		state.setLastChallengerSeenAt(lastSeenAt);
		return state;
	}

	private final Map<UUID, Player> playersByUuid = new HashMap<>();

	private Player insidePlayer(int gangId, Map<UUID, Turf> cache) {
		Player player = CaptureFixtures.player();
		users.register(player, CaptureFixtures.userInGang(gangId));
		cache.put(player.getUniqueId(), turf);
		playersByUuid.put(player.getUniqueId(), player);
		return player;
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
	@DisplayName("CLAIM fill rate is a flat base whenever anyone is inside, regardless of head-count or gang mix")
	void claimPhase_fillRateIsFlatRegardlessOfHeadcount() {
		CaptureService service = service(4, 4, 15); // base = 25
		stateAt(CapturePhase.CLAIM, 0.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> soloCache = new HashMap<>();
		insidePlayer(gangA.getId(), soloCache);

		tick(service, soloCache);
		double soloResult = turfManager.getRuntimeState(turf.getId()).getCaptureProgress();

		// Reset and repeat with 5 players spread across both gangs.
		stateAt(CapturePhase.CLAIM, 0.0, gangA.getId(), System.currentTimeMillis());
		playersByUuid.clear();
		Map<UUID, Turf> crowdedCache = new HashMap<>();
		for (int i = 0; i < 3; i++) {
			insidePlayer(gangA.getId(), crowdedCache);
		}
		for (int i = 0; i < 2; i++) {
			insidePlayer(gangB.getId(), crowdedCache);
		}

		tick(service, crowdedCache);
		double crowdedResult = turfManager.getRuntimeState(turf.getId()).getCaptureProgress();

		assertEquals(25.0, soloResult);
		assertEquals(soloResult, crowdedResult, "5 players fill Phase 1 at exactly the same rate as 1");
	}

	@Test
	@DisplayName("CLAIM freezes (does not decay) while empty within the abandon-grace window")
	void claimPhase_freezesWhileEmptyWithinGrace() {
		CaptureService service = service(4, 4, 30);
		stateAt(CapturePhase.CLAIM, 40.0, gangA.getId(), System.currentTimeMillis());

		tick(service, new HashMap<>());

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(TurfState.CONTESTING, state.getState());
		assertEquals(40.0, state.getCaptureProgress(), "empty Phase 1 freezes instead of decaying");
	}

	@Test
	@DisplayName("CLAIM cancels ABANDONED once the abandon-grace window has elapsed while empty")
	void claimPhase_cancelsAbandonedAfterGraceExpires() {
		CaptureService service = service(4, 4, 15);
		long            longAgo = System.currentTimeMillis() - 16_000L;
		stateAt(CapturePhase.CLAIM, 40.0, gangA.getId(), longAgo);

		tick(service, new HashMap<>());

		assertEquals(TurfState.IDLE, turfManager.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("CLAIM completing with a single dominant gang transitions to CONSOLIDATE at progress 0")
	void claimComplete_singleDominantGangElectsConsolidatePhase() {
		CaptureService service = service(4, 4, 15); // base = 25
		stateAt(CapturePhase.CLAIM, 90.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangA.getId(), cache);
		insidePlayer(gangA.getId(), cache);
		insidePlayer(gangB.getId(), cache); // present but outnumbered — gangA is dominant

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(playersByUuid.get(id));
			}
			service.tick(cache);

			TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
			assertEquals(CapturePhase.CONSOLIDATE, state.getPhase());
			assertEquals(0.0, state.getCaptureProgress());
			assertEquals(gangA.getId(), state.getChallengerGangId());

			// before=90 is already past every configured milestone (25/50/75), so this tick fires no progress
			// event — only the phase-transition's re-fired TurfCaptureStartEvent.
			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), times(1)).callEvent(captor.capture());
			assertInstanceOf(TurfCaptureStartEvent.class, captor.getValue(),
			                 "the CLAIM->CONSOLIDATE transition re-fires TurfCaptureStartEvent");
		}
	}

	@Test
	@DisplayName("CLAIM completing on a tie between gangs stalls at progress 100 instead of transitioning")
	void claimComplete_tieStallsAtOneHundred() {
		CaptureService service = service(4, 4, 15); // base = 25
		stateAt(CapturePhase.CLAIM, 90.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangA.getId(), cache);
		insidePlayer(gangB.getId(), cache); // exactly tied, 1 each

		tick(service, cache);

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(CapturePhase.CLAIM, state.getPhase(), "a tie leaves the phase parked at CLAIM");
		assertEquals(100.0, state.getCaptureProgress());
	}

	@Test
	@DisplayName("CONSOLIDATE tug-of-war: capturing gang minus opposers scales the delta")
	void consolidatePhase_capturingMinusOpposersScalesDelta() {
		CaptureService service = service(180, 4, 15); // phase2 base = 25
		stateAt(CapturePhase.CONSOLIDATE, 50.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangA.getId(), cache);
		insidePlayer(gangA.getId(), cache);
		insidePlayer(gangB.getId(), cache); // 2 capturing vs 1 opposer -> net +1

		tick(service, cache);

		assertEquals(75.0, turfManager.getRuntimeState(turf.getId()).getCaptureProgress(), 1e-9);
	}

	@Test
	@DisplayName("CONSOLIDATE reaching 100 completes the capture and hands the turf to the challenger")
	void consolidateComplete_transfersOwnership() {
		CaptureService service = service(180, 1, 15); // phase2 base = 100
		stateAt(CapturePhase.CONSOLIDATE, 50.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangA.getId(), cache);

		tick(service, cache);

		assertEquals(gangA.getId(), turf.getOwnerGangId());
		assertEquals(TurfState.COOLDOWN, turfManager.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("CONSOLIDATE rolling back to 0 reverts to CLAIM at progress 100 with the challenger cleared")
	void consolidateRollback_revertsToClaimAtOneHundredWithNoChallenger() {
		CaptureService service = service(180, 1, 15); // phase2 base = 100
		stateAt(CapturePhase.CONSOLIDATE, 50.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> cache = new HashMap<>();
		insidePlayer(gangB.getId(), cache); // pure opposition, no capturing-gang member present

		tick(service, cache);

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(CapturePhase.CLAIM, state.getPhase());
		assertEquals(100.0, state.getCaptureProgress());
		assertNull(state.getChallengerGangId());
		assertNull(turf.getOwnerGangId(), "the turf itself never touched ownership during the rollback");
	}

	@Test
	@DisplayName("milestone events split claim/consolidate progress by which phase is currently active")
	void milestoneEvents_splitByActivePhase() {
		CaptureService service = service(4, 4, 15); // base = 25
		stateAt(CapturePhase.CLAIM, 0.0, gangA.getId(), System.currentTimeMillis());
		Map<UUID, Turf> claimCache = new HashMap<>();
		insidePlayer(gangA.getId(), claimCache);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : claimCache.keySet()) {
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(playersByUuid.get(id));
			}
			service.tick(claimCache);

			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager()).callEvent(captor.capture());
			TurfCaptureProgressEvent event = (TurfCaptureProgressEvent) captor.getValue();
			assertEquals(CapturePhase.CLAIM, event.getPhase());
			assertEquals(25.0, event.getClaimProgress());
			assertEquals(0.0, event.getConsolidateProgress());
		}
	}
}
