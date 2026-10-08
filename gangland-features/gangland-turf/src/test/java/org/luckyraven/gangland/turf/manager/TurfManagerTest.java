package org.luckyraven.gangland.turf.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.InMemoryTurfSequence;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link TurfManager}'s registry bookkeeping: id allocation and its restart-time re-seeding, per-world
 * {@code findConflict} scoping, and index consistency (by-id map, by-world list, runtime states) across
 * {@code create}/{@code delete}. Ties to the "Pure-logic candidates" bullet in turf.md's Test Surface section.
 *
 * <p>{@link #idAllocation_reusesHighestIdAfterDeleteAndRestart()} pins Observation #5 (turf.md): {@code nextId} is
 * seeded to {@code max(existingId)+1} on every {@code initialize()}, so deleting the highest-id turf and restarting
 * re-uses that id — a brand-new turf then inherits any orphaned {@code turf_garrison}/{@code turf_active_buff}/
 * {@code turf_powerup_npc} rows still keyed to the old id.
 */
@DisplayName("TurfManager — id allocation, conflict scoping, index consistency")
class TurfManagerTest {

	private InMemoryTurfRepository repository;
	private TurfManager            manager;

	@BeforeEach
	void setUp() {
		repository = new InMemoryTurfRepository();
		manager    = new TurfManager(repository);
	}

	private static Turf turf(int id, String world, int x1, int z1, int x2, int z2) {
		return new Turf(id, "Turf " + id, new CuboidRegion(world, x1, z1, x2, z2), null,
		                BigDecimal.valueOf(100), System.currentTimeMillis(), 0L);
	}

	@Test
	@DisplayName("allocateId starts at 1 with no persisted turfs")
	void allocateId_startsAtOneWithNoTurfs() {
		assertEquals(1, manager.allocateId());
		assertEquals(2, manager.allocateId());
		assertEquals(3, manager.allocateId());
	}

	@Test
	@DisplayName("initialize seeds the id allocator to one past the highest persisted id")
	void initialize_seedsAllocatorToMaxPlusOne() {
		repository.seed(turf(3, "world", 0, 0, 10, 10));
		repository.seed(turf(7, "world", 20, 20, 30, 30));

		manager.initialize();

		assertEquals(8, manager.allocateId());
	}

	@Test
	@DisplayName("TF-05: deleting the highest-id turf then restarting never hands its id to a new turf")
	void idAllocation_neverReusesHighestIdAfterDeleteAndRestart() {
		InMemoryTurfSequence sequence = new InMemoryTurfSequence();
		repository.seed(turf(1, "world", 0, 0, 10, 10));
		repository.seed(turf(2, "world", 20, 20, 30, 30));
		repository.seed(turf(3, "world", 40, 40, 50, 50));
		manager = new TurfManager(repository, sequence);
		manager.initialize();
		assertEquals(4, manager.allocateId(), "first session allocates past the highest seeded id");

		Turf highest = manager.get(3);
		manager.delete(highest);

		// Simulate a restart: a fresh TurfManager over the same repository and the same persisted sequence.
		TurfManager restarted = new TurfManager(repository, sequence);
		restarted.initialize();

		assertEquals(5, restarted.allocateId(),
		             "id 4 was handed out before the restart and must stay used; 3 must not come back");
	}

	@Test
	@DisplayName("findConflict only scans the same world's turfs")
	void findConflict_scopesToWorld() {
		manager.create(turf(1, "world", 0, 0, 10, 10));
		manager.create(turf(2, "world_nether", 0, 0, 10, 10));

		CuboidRegion overlappingOverworld = new CuboidRegion("world", 5, 5, 15, 15);
		CuboidRegion overlappingNether    = new CuboidRegion("world_nether", 5, 5, 15, 15);
		CuboidRegion nonOverlappingThirdWorld = new CuboidRegion("world_the_end", 5, 5, 15, 15);

		assertEquals(1, manager.findConflict(overlappingOverworld).getId());
		assertEquals(2, manager.findConflict(overlappingNether).getId());
		assertNull(manager.findConflict(nonOverlappingThirdWorld), "no turfs exist in that world at all");
	}

	@Test
	@DisplayName("findConflict returns null for a disjoint region in a world that already has turfs")
	void findConflict_nullWhenDisjointInSameWorld() {
		manager.create(turf(1, "world", 0, 0, 10, 10));

		CuboidRegion disjoint = new CuboidRegion("world", 100, 100, 110, 110);

		assertNull(manager.findConflict(disjoint));
	}

	@Test
	@DisplayName("delete removes the turf from by-id, by-world and runtime-state indexes, and calls repository.delete")
	void delete_removesFromEveryIndex() {
		Turf turf = turf(5, "world", 0, 0, 10, 10);
		manager.create(turf);
		assertNotNull(manager.get(5));
		assertNotNull(manager.getRuntimeState(5));
		assertTrue(manager.getTurfsInWorld("world").contains(turf));

		manager.delete(turf);

		assertNull(manager.get(5));
		assertNull(manager.getRuntimeState(5));
		assertFalse(manager.getTurfsInWorld("world").contains(turf));
		assertTrue(repository.deleted.contains(turf));
	}

	@Test
	@DisplayName("create registers a fresh IDLE runtime state and persists the turf")
	void create_registersRuntimeStateAndPersists() {
		Turf turf = turf(9, "world", 0, 0, 10, 10);

		manager.create(turf);

		assertNotNull(manager.getRuntimeState(9));
		assertEquals(turf, repository.loadAll().stream().filter(t -> t.getId() == 9).findFirst().orElseThrow());
	}

	@Test
	@DisplayName("TF-34: getTurfsInWorld returns a list callers cannot mutate")
	void getTurfsInWorld_isUnmodifiable() {
		manager.create(turf(1, "world", 0, 0, 10, 10));

		List<Turf> view = manager.getTurfsInWorld("world");

		assertThrows(UnsupportedOperationException.class, () -> view.add(turf(2, "world", 20, 20, 30, 30)));
	}

	@Test
	@DisplayName("TF-34: iterating getTurfsInWorld while a turf is created in that world does not throw")
	void getTurfsInWorld_iterationSurvivesCreate() {
		manager.create(turf(1, "world", 0, 0, 10, 10));

		int seen = 0;
		for (Turf ignored : manager.getTurfsInWorld("world")) {
			seen++;
			manager.create(turf(100 + seen, "world", 100 * seen, 0, 100 * seen + 10, 10));
		}

		assertEquals(1, seen, "the loop walks the snapshot it started with");
		assertEquals(2, manager.getTurfsInWorld("world").size());
	}
}
