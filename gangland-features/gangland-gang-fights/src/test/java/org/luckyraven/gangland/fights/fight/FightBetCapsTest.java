package org.luckyraven.gangland.fights.fight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.FightRules;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FightBetCaps - bet and ranked limits")
class FightBetCapsTest {

	private FightBetCaps caps;

	@BeforeEach
	void setUp() {
		FightRules rules = mock(FightRules.class);
		when(rules.getMaxBet()).thenReturn(100_000D);
		when(rules.getMaxBetPercentOfBank()).thenReturn(50);
		when(rules.getMaxNetTransferPerPairPerDay()).thenReturn(200_000D);
		when(rules.getMaxBetTransferPerGangPerDay()).thenReturn(300_000D);
		when(rules.getRankedMinOpponentGangAgeDays()).thenReturn(7);
		when(rules.getRankedPerPairPerDay()).thenReturn(3);
		when(rules.getRematchCooldownMinutes()).thenReturn(30);
		caps = new FightBetCaps(rules);
	}

	private static BigDecimal d(long v) {
		return BigDecimal.valueOf(v);
	}

	@Test
	@DisplayName("percentOfBothBanks: the poorer bank decides, at accept too")
	void percentOfBothBanks() {
		assertNull(caps.checkBet(500, d(1000), d(1000)));
		assertEquals("Economy.Bet_Over_Percent", caps.checkBet(501, d(1000), d(5000)));
		assertEquals("Economy.Bet_Over_Percent", caps.checkBet(501, d(5000), d(1000)));
	}

	@Test
	@DisplayName("maxBet: an absolute cap")
	void maxBet() {
		assertEquals("Economy.Bet_Too_High", caps.checkBet(100_001, d(10_000_000), d(10_000_000)));
		assertNull(caps.checkBet(0, d(0), d(0)));
	}

	@Test
	@DisplayName("pairDailyCap: net flow between the pair plus the new bet")
	void pairDailyCap() {
		assertNull(caps.checkTransfer(50_000, 150_000, 0, 0));
		assertEquals("Economy.Pair_Cap", caps.checkTransfer(50_001, 150_000, 0, 0));
		assertEquals("Economy.Pair_Cap", caps.checkTransfer(50_001, -150_000, 0, 0));
	}

	@Test
	@DisplayName("gangDailyCap: either gang's day winnings plus the new bet")
	void gangDailyCap() {
		assertNull(caps.checkTransfer(1000, 0, 299_000, 0));
		assertEquals("Economy.Gang_Cap", caps.checkTransfer(1001, 0, 299_000, 0));
		assertEquals("Economy.Gang_Cap", caps.checkTransfer(1001, 0, 0, 299_000));
	}

	@Test
	@DisplayName("opponentAge: ranked needs an old enough opponent, a free pair slot and a spent cooldown")
	void opponentAge() {
		assertNull(caps.checkRanked(7, 0, Long.MAX_VALUE));
		assertEquals("Economy.Ranked_Opponent_Too_Young", caps.checkRanked(6, 0, Long.MAX_VALUE));
		assertEquals("Economy.Ranked_Pair_Limit", caps.checkRanked(30, 3, Long.MAX_VALUE));
		assertEquals("Economy.Ranked_Rematch_Cooldown", caps.checkRanked(30, 1, 29 * 60_000L));
		assertNull(caps.checkRanked(30, 2, 30 * 60_000L));
	}
}
