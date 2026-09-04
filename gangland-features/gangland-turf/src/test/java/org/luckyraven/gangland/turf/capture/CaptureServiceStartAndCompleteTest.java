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
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.CapturePhase;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

/**
 * Proves {@code CaptureService.tickIdle} (contest start conditions, W6 in turf.md) and {@code complete} (ownership
 * transfer, W9). Ties to the "CaptureService maths" Test Surface bullet.
 *
 * <p>{@link #complete_writesDanglingOwnerIdWhenChallengerGangIsGone()} pins Observation #1 (turf.md, High risk):
 * {@code turf.setOwnerGangId(newOwnerId)} and {@code turfs.persist(turf)} run <b>before</b> the
 * {@code newOwner != null} null check, so a capture completing against a disbanded gang still writes the dangling
 * owner id and moves the turf to COOLDOWN, but fires no {@link TurfCapturedEvent} — nothing downstream (boss bars,
 * defenders, the Quartermaster) is ever cleaned up until the income task's next pass auto-releases the turf.
 *
 * <p>{@link #tieOnUnclaimedTurf_startsWithTheLowestGangIdInstead()} shows {@code tickIdle}'s tie-break differs from
 * the mid-contest CLAIM→CONSOLIDATE election: a start-time tie falls back to the lowest gang id and starts anyway,
 * it does not stall.
 */
@DisplayName("CaptureService — contest start (tickIdle) and completion")
class CaptureServiceStartAndCompleteTest {

	private InMemoryTurfRepository     repository;
	private TurfManager                turfManager;
	private FakeGangLookup             gangs;
	private FakeUserLookup             users;
	private RecordingTurfSoundContract sounds;

	private Gang owner;
	private Gang challenger;

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
	}

	private CaptureService service(int... progressMilestones) {
		CaptureSettings settings = CaptureFixtures.settings(180, 15, 15, 10, progressMilestones, 90, 90);
		return new CaptureService(turfManager, gangs, users, settings, sounds);
	}

	private Turf createTurf(Integer ownerGangId) {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), ownerGangId, BigDecimal.TEN,
		                     0L, 0L);
		turfManager.create(turf);
		return turf;
	}

	private final Map<UUID, Player> playersByUuid = new HashMap<>();

	private Player playerInGang(int gangId, Map<UUID, Turf> cache, Turf turf) {
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
	@DisplayName("owned turf: a lone challenger with zero defenders starts the contest — no minimum head-count")
	void ownedTurf_soleChallengerStartsContest() {
		Turf            turf    = createTurf(owner.getId());
		CaptureService  service = service(25, 50, 75);
		Map<UUID, Turf> cache   = new HashMap<>();
		playerInGang(challenger.getId(), cache, turf);

		tick(service, cache);

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(TurfState.CONTESTING, state.getState());
		assertEquals(CapturePhase.CLAIM, state.getPhase());
		assertEquals(0.0, state.getCaptureProgress());
		assertEquals(challenger.getId(), state.getChallengerGangId());
	}

	@Test
	@DisplayName("owned turf: any defender present blocks the start")
	void ownedTurf_defenderPresenceBlocksStart() {
		Turf            turf    = createTurf(owner.getId());
		CaptureService  service = service(25, 50, 75);
		Map<UUID, Turf> cache   = new HashMap<>();
		playerInGang(challenger.getId(), cache, turf);
		playerInGang(owner.getId(), cache, turf);

		tick(service, cache);

		assertEquals(TurfState.IDLE, turfManager.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("owned turf: two distinct challenger gangs cancel each other out and neither starts a contest")
	void ownedTurf_multipleChallengerGangsBlockStart() {
		Turf            turf       = createTurf(owner.getId());
		Gang            thirdGang  = CaptureFixtures.gang(3);
		gangs.register(thirdGang);
		CaptureService  service = service(25, 50, 75);
		Map<UUID, Turf> cache   = new HashMap<>();
		playerInGang(challenger.getId(), cache, turf);
		playerInGang(thirdGang.getId(), cache, turf);

		tick(service, cache);

		assertEquals(TurfState.IDLE, turfManager.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("owned turf: still within cooldown blocks the start even with a lone challenger present")
	void ownedTurf_withinCooldownBlocksStart() {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), owner.getId(), BigDecimal.TEN,
		                     0L, System.currentTimeMillis());
		turfManager.create(turf);
		CaptureService  service = service(25, 50, 75);
		Map<UUID, Turf> cache   = new HashMap<>();
		playerInGang(challenger.getId(), cache, turf);

		tick(service, cache);

		assertEquals(TurfState.IDLE, turfManager.getRuntimeState(turf.getId()).getState());
	}

	@Test
	@DisplayName("unclaimed turf: a single member of one gang is enough to start the contest")
	void unclaimedTurf_singleMemberStartsContest() {
		Turf            turf    = createTurf(null);
		CaptureService  service = service(25, 50, 75);
		Map<UUID, Turf> cache   = new HashMap<>();
		playerInGang(challenger.getId(), cache, turf);

		tick(service, cache);

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(TurfState.CONTESTING, state.getState());
		assertEquals(challenger.getId(), state.getChallengerGangId());
	}

	@Test
	@DisplayName("unclaimed turf: a tie on start falls back to the lowest gang id instead of stalling")
	void tieOnUnclaimedTurf_startsWithTheLowestGangIdInstead() {
		Turf            turf    = createTurf(null);
		CaptureService  service = service(25, 50, 75);
		Map<UUID, Turf> cache   = new HashMap<>();
		playerInGang(challenger.getId(), cache, turf); // id 2
		playerInGang(owner.getId(), cache, turf);      // id 1 — reused here purely as "the lower id gang"

		tick(service, cache);

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(TurfState.CONTESTING, state.getState(), "start-time ties do not block the contest");
		assertEquals(owner.getId(), state.getChallengerGangId(), "the lowest gang id wins the tie-break at start");
	}

	@Test
	@DisplayName("complete transfers ownership, starts cooldown, and fires TurfCapturedEvent when the gang exists")
	void complete_transfersOwnershipAndFiresEvent() {
		Turf            turf    = createTurf(owner.getId());
		CaptureService  service = service(); // no milestones — isolate the completion event
		TurfRuntimeState state  = turfManager.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(challenger.getId());
		// base fill is 100/180 = ~0.56 per tick, so start close enough that one tick crosses 100.
		state.setCaptureProgress(99.9);
		Map<UUID, Turf> cache = new HashMap<>();
		playerInGang(challenger.getId(), cache, turf);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(playersByUuid.get(id));
			}
			service.tick(cache);

			assertEquals(challenger.getId(), turf.getOwnerGangId());
			assertEquals(TurfState.COOLDOWN, state.getState());
			verify(bukkit.pluginManager()).callEvent(org.mockito.ArgumentMatchers.isA(TurfCapturedEvent.class));
		}
	}

	@Test
	@DisplayName("complete against a disbanded challenger gang writes the dangling owner id but fires no event (Observation #1)")
	void complete_writesDanglingOwnerIdWhenChallengerGangIsGone() {
		Turf turf = createTurf(owner.getId());
		// Simulate the challenger disbanding mid-contest: never registered in the gang lookup.
		Gang ghostChallenger = CaptureFixtures.gang(999);
		CaptureService   service = service(); // no milestones
		TurfRuntimeState state   = turfManager.getRuntimeState(turf.getId());
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(ghostChallenger.getId());
		// base fill is 100/180 = ~0.56 per tick, so start close enough that one tick crosses 100.
		state.setCaptureProgress(99.9);
		Map<UUID, Turf> cache = new HashMap<>();
		playerInGang(ghostChallenger.getId(), cache, turf);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(playersByUuid.get(id));
			}
			service.tick(cache);

			assertEquals(ghostChallenger.getId(), turf.getOwnerGangId(),
			            "the dangling owner id is written even though the gang record does not exist");
			assertEquals(TurfState.COOLDOWN, state.getState());
			verify(bukkit.pluginManager(), never()).callEvent(any());
		}
	}
}
