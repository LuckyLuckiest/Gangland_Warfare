package org.luckyraven.gangland.copsncrooks.bag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lane bag: the pure {@link HotCashBags} store (cents in BigDecimal, no Bukkit). */
@DisplayName("HotCashBags - the per-player store")
class HotCashBagsTest {

	private static final UUID RUNNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID OTHER  = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	private final HotCashBags bags = new HotCashBags(null);

	private static BigDecimal bd(String value) {
		return new BigDecimal(value);
	}

	private static void same(String expected, BigDecimal actual) {
		assertEquals(0, bd(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
	}

	@Test
	@DisplayName("reading an unknown player does not create a row (the HUD reads every online player)")
	void readsDoNotCreateRows() {
		bags.amount(RUNNER);
		bags.seized(RUNNER);
		bags.chaseTaken(RUNNER);

		assertTrue(bags.all().isEmpty(), bags.all().toString());
	}

	@Test
	@DisplayName("a player with no row has an empty bag")
	void unknownPlayerIsEmpty() {
		same("0", bags.amount(RUNNER));
		same("0", bags.chaseTaken(RUNNER));
		same("0", bags.seized(RUNNER));
	}

	@Test
	@DisplayName("P-BAG-10: the chase cap refuses an overflow and adds nothing")
	void chaseCapRefusesOverflow() {
		assertTrue(bags.add(RUNNER, bd("4900"), bd("5000")));

		assertFalse(bags.add(RUNNER, bd("200"), bd("5000")));

		same("4900", bags.amount(RUNNER));
		same("4900", bags.chaseTaken(RUNNER));
		assertTrue(bags.add(RUNNER, bd("100"), bd("5000")), "exactly at the cap fits");
	}

	@Test
	@DisplayName("a cap of 0 is no cap")
	void zeroCapAcceptsAnything() {
		assertTrue(bags.add(RUNNER, bd("1000000"), BigDecimal.ZERO));
	}

	@Test
	@DisplayName("P-BAG-12: takeAll returns the bag and it reads 0 immediately; the second take returns 0")
	void takeAllZeroesBeforeReturning() {
		bags.add(RUNNER, bd("300"), BigDecimal.ZERO);

		same("300", bags.takeAll(RUNNER));
		same("0", bags.amount(RUNNER));
		same("0", bags.chaseTaken(RUNNER));
		same("0", bags.takeAll(RUNNER));
	}

	@Test
	@DisplayName("the fence take keeps the chase counter")
	void takeAllKeepChaseKeepsCounter() {
		bags.add(RUNNER, bd("300"), BigDecimal.ZERO);

		same("300", bags.takeAllKeepChase(RUNNER));

		same("0", bags.amount(RUNNER));
		same("300", bags.chaseTaken(RUNNER));
	}

	@Test
	@DisplayName("debit removes at most what is there")
	void debitIsClamped() {
		bags.add(RUNNER, bd("300"), BigDecimal.ZERO);

		bags.debit(RUNNER, bd("100"));
		same("200", bags.amount(RUNNER));

		bags.debit(RUNNER, bd("900"));
		same("0", bags.amount(RUNNER));
	}

	@Test
	@DisplayName("P-BAG-4: the daily allowance is the cap less today's banked cash and starts over on a new day")
	void dailyAllowanceResetsOnNewDay() {
		bags.recordBanked(RUNNER, 10L, bd("15000"));

		same("0", bags.allowance(RUNNER, 10L, bd("15000"), bd("99999")));
		same("15000", bags.allowance(RUNNER, 11L, bd("15000"), bd("99999")));
	}

	@Test
	@DisplayName("the allowance never exceeds what is asked, and a cap of 0 allows everything asked")
	void allowanceIsBoundedByTheAsk() {
		bags.recordBanked(RUNNER, 10L, bd("14000"));

		same("1000", bags.allowance(RUNNER, 10L, bd("15000"), bd("99999")));
		same("400", bags.allowance(RUNNER, 10L, bd("15000"), bd("400")));
		same("99999", bags.allowance(RUNNER, 10L, BigDecimal.ZERO, bd("99999")));
	}

	@Test
	@DisplayName("recordBanked accumulates within a day")
	void recordBankedAccumulates() {
		bags.recordBanked(RUNNER, 10L, bd("100"));
		bags.recordBanked(RUNNER, 10L, bd("250"));

		same("350", bags.entry(RUNNER).bankedToday());
		assertEquals(10L, bags.entry(RUNNER).bankedDay());
	}

	@Test
	@DisplayName("a zeroed entry stays in the data supplier so its saved row is overwritten")
	void zeroedEntryStaysInSupplier() {
		bags.add(RUNNER, bd("300"), BigDecimal.ZERO);
		bags.add(OTHER, bd("5"), BigDecimal.ZERO);

		bags.takeAll(RUNNER);

		Collection<BagEntry> all = bags.all();
		assertEquals(2, all.size());
		assertTrue(all.stream().anyMatch(e -> e.player().equals(RUNNER) && e.amount().signum() == 0));
	}

	@Test
	@DisplayName("setSeized stores the amount, zero included")
	void setSeizedOverwrites() {
		bags.setSeized(RUNNER, bd("800"));
		same("800", bags.seized(RUNNER));

		bags.setSeized(RUNNER, BigDecimal.ZERO);
		same("0", bags.seized(RUNNER));
	}

	@Test
	@DisplayName("put seeds a row (the load path)")
	void putSeedsARow() {
		bags.put(new BagEntry(RUNNER, bd("500"), bd("500"), 9L, bd("20"), bd("7")));

		same("500", bags.amount(RUNNER));
		same("7", bags.seized(RUNNER));
		assertEquals(9L, bags.entry(RUNNER).bankedDay());
	}
}
