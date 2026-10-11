package org.luckyraven.gangland.fights.fight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.economy.EconomyHandler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FightSettlement - bank to bank, zero-sum")
class FightSettlementTest {

	private final Map<Integer, EconomyHandler> banks   = new HashMap<>();
	private final List<FightResult>            results = new ArrayList<>();

	private BigDecimal max = BigDecimal.valueOf(1_000_000);
	private int        cut = 0;

	@BeforeEach
	void setUp() {
		banks.put(1, bank(5000));
		banks.put(2, bank(5000));
	}

	private static EconomyHandler bank(long amount) {
		return new EconomyHandler(BigDecimal.valueOf(amount), null, false);
	}

	private FightSettlement settlement() {
		return new FightSettlement(banks::get, () -> max, () -> cut, results::add, () -> 1000L);
	}

	private static Fight fight(double bet) {
		return new Fight(1L, 1, 2, 2, bet, true, 0L);
	}

	private long total() {
		return banks.values().stream().mapToLong(b -> b.getAmount().longValue()).sum();
	}

	private long bal(int gang) {
		return banks.get(gang).getAmount().longValue();
	}

	@Test
	@DisplayName("win: both pay at accept, the winner ends +2000 total, the loser -1000 from the start")
	void win() {
		FightSettlement s     = settlement();
		Fight           fight = fight(1000);

		assertTrue(s.escrow(fight).ok());
		assertEquals(4000, bal(1));
		assertEquals(4000, bal(2));

		s.settle(fight, Outcome.WIN_A);

		assertEquals(6000, bal(1));
		assertEquals(4000, bal(2));
		assertEquals(10_000, total());
		assertEquals(1, results.size());
		assertEquals(1, results.get(0).winnerGangId());
		assertEquals(2, results.get(0).loserGangId());
	}

	@Test
	@DisplayName("house cut: the server keeps its percent of the pot")
	void houseCut() {
		cut = 10;
		FightSettlement s     = settlement();
		Fight           fight = fight(1000);

		s.escrow(fight);
		s.settle(fight, Outcome.WIN_B);

		assertEquals(5800, bal(2));
		assertEquals(4000, bal(1));
	}

	@Test
	@DisplayName("draw: each gang gets its stake back")
	void draw() {
		FightSettlement s     = settlement();
		Fight           fight = fight(1000);

		s.escrow(fight);
		s.settle(fight, Outcome.DRAW);

		assertEquals(5000, bal(1));
		assertEquals(5000, bal(2));
		assertEquals(-1, results.get(0).winnerGangId());
		assertTrue(results.get(0).draw());
	}

	@Test
	@DisplayName("capOverflowToLoser: what would exceed Maximum_Balance goes back to the loser")
	void capOverflowToLoser() {
		max = BigDecimal.valueOf(5500);
		FightSettlement s     = settlement();
		Fight           fight = fight(1000);

		s.escrow(fight);
		s.settle(fight, Outcome.WIN_A);

		assertEquals(5500, bal(1));
		assertEquals(4500, bal(2));
		assertEquals(10_000, total());
	}

	@Test
	@DisplayName("secondWithdrawThrows_firstRefunded: gang B cannot pay, gang A is whole again")
	void secondWithdrawThrows_firstRefunded() {
		banks.put(2, bank(500));
		FightSettlement s = settlement();

		EscrowResult result = s.escrow(fight(1000));

		assertFalse(result.ok());
		assertEquals("Economy.Cannot_Afford_B", result.failureKey());
		assertEquals(5000, bal(1));
		assertEquals(500, bal(2));
		assertFalse(s.holds(fight(1000)));
	}

	@Test
	@DisplayName("first gang cannot pay: nothing is taken")
	void firstWithdrawThrows() {
		banks.put(1, bank(10));
		FightSettlement s = settlement();

		assertEquals("Economy.Cannot_Afford_A", s.escrow(fight(1000)).failureKey());
		assertEquals(5000, bal(2));
	}

	@Test
	@DisplayName("abortAll_refundsEverything: every open escrow goes home, no result is recorded")
	void abortAll_refundsEverything() {
		FightSettlement s = settlement();
		Fight           f = fight(1000);
		Fight           g = new Fight(2L, 1, 2, 2, 250, false, 0L);

		s.escrow(f);
		s.escrow(g);
		assertEquals(3750, bal(1));

		s.abortAll();

		assertEquals(5000, bal(1));
		assertEquals(5000, bal(2));
		assertTrue(results.isEmpty());
		s.abortAll();
		assertEquals(10_000, total());
	}

	@Test
	@DisplayName("refund after a cancel returns the stakes once")
	void refund() {
		FightSettlement s = settlement();
		Fight           f = fight(1000);

		s.escrow(f);
		s.refund(f);
		s.refund(f);

		assertEquals(10_000, total());
	}

	@Test
	@DisplayName("a sparring fight (no bet) moves no money but still reports its result once")
	void noBet() {
		FightSettlement s = settlement();
		Fight           f = fight(0);

		assertTrue(s.escrow(f).ok());
		s.settle(f, Outcome.WIN_A);

		assertEquals(10_000, total());
		assertEquals(1, results.size());
	}

	@Test
	@DisplayName("the unwired settlement fails closed")
	void unwired() {
		assertFalse(new FightSettlement().escrow(fight(10)).ok());
	}
}
