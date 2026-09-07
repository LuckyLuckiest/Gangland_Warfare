package org.luckyraven.gangland.turf.powerups;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.support.InMemoryGarrisonRepository;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link GarrisonManager#add(int, int)} / {@link GarrisonManager#consume(int, int)} clamping. Ties to the
 * "GarrisonManager.add / consume clamping (consume more than stock, non-positive deltas, missing rows)" bullet in
 * turf.md's Test Surface section.
 */
@DisplayName("GarrisonManager — add/consume clamping")
class GarrisonManagerTest {

	private InMemoryGarrisonRepository repository;
	private GarrisonManager            manager;

	@BeforeEach
	void setUp() {
		repository = new InMemoryGarrisonRepository();
		manager    = new GarrisonManager(repository);
		manager.initialize();
	}

	@Test
	@DisplayName("count returns 0 for a turf with no garrison row")
	void count_zeroForMissingRow() {
		assertEquals(0, manager.count(99));
	}

	@Test
	@DisplayName("add creates a fresh row on first use and persists it")
	void add_createsRowOnFirstUse() {
		manager.add(1, 5);

		assertEquals(5, manager.count(1));
		assertEquals(1, repository.saved.size());
	}

	@Test
	@DisplayName("add accumulates onto an existing row")
	void add_accumulatesOntoExistingRow() {
		manager.add(1, 5);
		manager.add(1, 3);

		assertEquals(8, manager.count(1));
	}

	@Test
	@DisplayName("add with a zero or negative delta is a no-op")
	void add_nonPositiveDeltaIsNoOp() {
		manager.add(1, 0);
		manager.add(1, -5);

		assertEquals(0, manager.count(1));
		assertTrue(repository.saved.isEmpty());
	}

	@Test
	@DisplayName("consume returns 0 and does not create a row for a turf with no garrison")
	void consume_zeroForMissingRow() {
		int consumed = manager.consume(42, 3);

		assertEquals(0, consumed);
		assertEquals(0, manager.count(42));
	}

	@Test
	@DisplayName("consume with a zero or negative request is a no-op that returns 0")
	void consume_nonPositiveRequestIsNoOp() {
		manager.add(1, 5);

		assertEquals(0, manager.consume(1, 0));
		assertEquals(0, manager.consume(1, -1));
		assertEquals(5, manager.count(1), "stock is untouched by a non-positive request");
	}

	@Test
	@DisplayName("consume caps at the available stock rather than going negative")
	void consume_capsAtAvailableStock() {
		manager.add(1, 3);

		int consumed = manager.consume(1, 10);

		assertEquals(3, consumed, "only the 3 actually available are consumed");
		assertEquals(0, manager.count(1));
	}

	@Test
	@DisplayName("consume less than the stock takes exactly the requested amount and leaves the remainder")
	void consume_lessThanStockTakesExactAmount() {
		manager.add(1, 10);

		int consumed = manager.consume(1, 4);

		assertEquals(4, consumed);
		assertEquals(6, manager.count(1));
	}

	@Test
	@DisplayName("consume on an already-zero row returns 0 without going negative")
	void consume_onZeroStockRowReturnsZero() {
		manager.add(1, 3);
		manager.consume(1, 3); // drain to zero, row stays (not deleted)

		int secondConsume = manager.consume(1, 1);

		assertEquals(0, secondConsume);
		assertEquals(0, manager.count(1));
	}
}
