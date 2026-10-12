package org.luckyraven.gangland.turf.capture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inside counts a turf's runtime state keeps after each capture tick (read by the map and the boss bar): challengers
 * per gang, and owner plus allied defenders. Dead players are not counted.
 */
@DisplayName("CaptureService - inside counts recorded on each tick")
class CaptureServiceInsideCountsTest {

	private TurfManager                turfManager;
	private FakeGangLookup             gangs;
	private FakeUserLookup             users;
	private CaptureService             service;
	private final Map<UUID, Player>    playersByUuid = new HashMap<>();

	@BeforeEach
	void setUp() {
		turfManager = new TurfManager(new InMemoryTurfRepository());
		gangs       = new FakeGangLookup();
		users       = new FakeUserLookup();
		gangs.register(CaptureFixtures.gang(1));
		gangs.register(CaptureFixtures.gang(2));
		gangs.register(CaptureFixtures.gang(3));
		CaptureSettings settings = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		service = new CaptureService(turfManager, gangs, users, settings, new RecordingTurfSoundContract(),
		                             new TurfOwnership(turfManager));
	}

	private Turf ownedTurf() {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), 1, BigDecimal.TEN, 0L, 0L);
		turfManager.create(turf);
		return turf;
	}

	private void addPlayer(int gangId, Map<UUID, Turf> cache, Turf turf) {
		Player player = CaptureFixtures.player();
		users.register(player, CaptureFixtures.userInGang(gangId));
		playersByUuid.put(player.getUniqueId(), player);
		cache.put(player.getUniqueId(), turf);
	}

	private void tick(Map<UUID, Turf> cache) {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (UUID id : cache.keySet()) {
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(playersByUuid.get(id));
			}
			service.tick(cache);
		}
	}

	@Test
	@DisplayName("two challenger gangs and the owner's members are counted separately")
	void countsChallengersPerGangAndDefenders() {
		Turf            turf  = ownedTurf();
		Map<UUID, Turf> cache = new HashMap<>();
		addPlayer(2, cache, turf);
		addPlayer(2, cache, turf);
		addPlayer(3, cache, turf);
		addPlayer(1, cache, turf);

		tick(cache);

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertEquals(Map.of(2, 2, 3, 1), state.getChallengersInside());
		assertEquals(1, state.getDefendersInside());
	}

	@Test
	@DisplayName("an empty turf records no challengers and no defenders")
	void emptyTurfRecordsZero() {
		Turf turf = ownedTurf();

		tick(new HashMap<>());

		TurfRuntimeState state = turfManager.getRuntimeState(turf.getId());
		assertTrue(state.getChallengersInside().isEmpty());
		assertEquals(0, state.getDefendersInside());
	}
}
