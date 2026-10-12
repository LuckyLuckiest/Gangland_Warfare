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
	@DisplayName("setHolding_createsRowAtZeroStock: Hold on a turf with no row creates one; Defend on no row saves nothing")
	void setHolding_createsRowAtZeroStock() {
		manager.setHolding(5, true);

		assertTrue(manager.holding(5));
		assertEquals(0, manager.count(5));

		int saves = repository.saved.size();
		manager.setHolding(6, false);

		assertFalse(manager.holding(6));
		assertEquals(saves, repository.saved.size());
	}

	@Test
	@DisplayName("stockChangesKeepTheOrder: add, consume and refund leave the order alone")
	void stockChangesKeepTheOrder() {
		manager.setHolding(5, true);
		manager.add(5, 3);
		manager.consume(5, 1);
		manager.refund(5, 1, 12);

		assertTrue(manager.holding(5));
	}

	@Test
	@DisplayName("remove_resetsToDefend: an owner change drops the row, so the order is Defend again (T-270)")
	void remove_resetsToDefend() {
		manager.setHolding(5, true);
		manager.remove(5);

		assertFalse(manager.holding(5));
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

	@Test
	@DisplayName("GI-34: remove clears the in-memory count and deletes the persisted row (turf-delete cascade)")
	void remove_clearsInMemoryAndPersistedRow() {
		manager.add(5, 3);
		assertEquals(3, manager.count(5));

		manager.remove(5);

		assertEquals(0, manager.count(5), "a fresh turf reusing id 5 must not inherit the old stock");
		assertTrue(repository.loadAll().isEmpty(), "the persisted garrison row must be gone too");
	}

	@Test
	@DisplayName("GI-34: remove on a turf with no garrison row is a no-op, not an error")
	void remove_noExistingRow_isNoOp() {
		manager.remove(99);

		assertEquals(0, manager.count(99));
	}

	@Test
	@DisplayName("refund_addsBackCapped: survivors return to the reserve, never past Max_Reserve")
	void refund_addsBackCapped() {
		manager.add(1, 3);

		assertEquals(2, manager.refund(1, 2, 12));
		assertEquals(5, manager.count(1));

		manager.add(1, 6);
		assertEquals(1, manager.refund(1, 4, 12), "11 + 4 stops at 12");
		assertEquals(12, manager.count(1));
		assertEquals(0, manager.refund(1, 3, 12), "full reserve takes nothing");
		assertEquals(0, manager.refund(1, 0, 12));
		assertEquals(0, manager.refund(1, -2, 12));
	}
}
