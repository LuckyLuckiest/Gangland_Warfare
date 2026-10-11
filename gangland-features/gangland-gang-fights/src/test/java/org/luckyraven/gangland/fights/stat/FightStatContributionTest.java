package org.luckyraven.gangland.fights.stat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.database.FightPlayerStats;
import org.luckyraven.gangland.fights.database.FightRecord;
import org.luckyraven.gangland.fights.database.FightStats;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightSide;
import org.luckyraven.gangland.fights.fight.Participant;
import org.luckyraven.gangland.fights.placeholder.FightPlaceholderContribution;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("fight wins stat, history queries and wlr")
class FightStatContributionTest {

	private static final long DAY = FightHistory.DAY_MS;

	private FightHistory history;
	private FightRules   rules;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		history = new FightHistory(mock(IRepository.class), mock(IRepository.class), mock(IRepository.class));
		rules   = mock(FightRules.class);
		when(rules.getRankedBoardWinsPerOpponentPerDay()).thenReturn(1);
	}

	private Fight fight(int a, int b, double bet, boolean ranked) {
		return new Fight(1L, a, b, 1, bet, ranked, 0L);
	}

	@Test
	@DisplayName("winsInRanking: the key is wins and sparring wins do not count")
	void winsInRanking() {
		FightWinsStatContribution stat = new FightWinsStatContribution(history, rules);

		history.record(fight(1, 2, 1000, true), 1, 10 * DAY);
		history.record(fight(1, 3, 0, false), 1, 10 * DAY);

		assertEquals("wins", stat.key());
		assertEquals(1, stat.value(1));
		assertEquals(0, stat.value(2));
	}

	@Test
	@DisplayName("perOpponentDailyCap: only one win per opponent per day counts, a new day counts again")
	void perOpponentDailyCap() {
		FightWinsStatContribution stat = new FightWinsStatContribution(history, rules);

		history.record(fight(1, 2, 1000, true), 1, 10 * DAY + 1);
		history.record(fight(2, 1, 1000, true), 1, 10 * DAY + 2);
		history.record(fight(1, 3, 1000, true), 1, 10 * DAY + 3);
		assertEquals(2, stat.value(1));

		history.record(fight(1, 2, 1000, true), 1, 11 * DAY);
		assertEquals(3, stat.value(1));

		when(rules.getRankedBoardWinsPerOpponentPerDay()).thenReturn(2);
		assertEquals(4, stat.value(1));
	}

	@Test
	@DisplayName("a result updates gang and fighter rows; a draw only the gang rows")
	void statsUpdated() {
		UUID winner = UUID.randomUUID();
		UUID loser  = UUID.randomUUID();
		Fight fight = fight(1, 2, 100, true);
		fight.addParticipant(new Participant(winner, 1, FightSide.A));
		fight.addParticipant(new Participant(loser, 2, FightSide.B));

		history.record(fight, 1, DAY);
		history.record(fight, -1, DAY);

		assertEquals(new FightStats(1, 1, 0, 1), history.stats(1));
		assertEquals(new FightStats(2, 0, 1, 1), history.stats(2));
		assertEquals(new FightPlayerStats(winner, 1, 0, 0), history.playerStats(winner));
		assertEquals(new FightPlayerStats(loser, 0, 1, 0), history.playerStats(loser));
	}

	@Test
	@DisplayName("a sparring fight adds a record row but changes no win/loss stats")
	void sparringNoStats() {
		UUID winner = UUID.randomUUID();
		Fight fight = fight(1, 2, 0, false);
		fight.addParticipant(new Participant(winner, 1, FightSide.A));

		history.record(fight, 1, DAY);

		assertEquals(1, history.records().size());
		assertEquals(new FightStats(1, 0, 0, 0), history.stats(1));
		assertEquals(new FightStats(2, 0, 0, 0), history.stats(2));
		assertEquals(new FightPlayerStats(winner, 0, 0, 0), history.playerStats(winner));
	}

	@Test
	@DisplayName("daily money, ranked counts and rematch gap read the records")
	void dailyQueries() {
		history.record(fight(1, 2, 1000, true), 1, 5 * DAY + 100);
		history.record(fight(2, 1, 400, true), 2, 5 * DAY + 200);
		history.record(fight(1, 3, 700, false), 1, 5 * DAY + 300);

		assertEquals(600, history.pairNetToday(1, 2, 5 * DAY + 500));
		assertEquals(1700, history.wonToday(1, 5 * DAY + 500));
		assertEquals(0, history.wonToday(1, 6 * DAY));
		assertEquals(2, history.rankedBetweenToday(1, 2, 5 * DAY + 500));
		assertEquals(300, history.millisSinceLastRanked(2, 1, 5 * DAY + 500));
		assertEquals(Long.MAX_VALUE, history.millisSinceLastRanked(1, 3, 5 * DAY + 500));
	}

	@Test
	@DisplayName("placeholders: wlr is wins per loss, the win count with no loss")
	void wlr() {
		assertEquals("2.50", FightPlaceholderContribution.wlr(new FightStats(1, 5, 2, 0)));
		assertEquals("3.00", FightPlaceholderContribution.wlr(new FightStats(1, 3, 0, 4)));
		assertNull(new FightPlaceholderContribution(history, null).resolve(null, "user_name"));
	}

	@Test
	@DisplayName("record ids count up")
	void ids() {
		FightRecord first  = history.record(fight(1, 2, 0, false), 1, DAY);
		FightRecord second = history.record(fight(1, 2, 0, false), 1, DAY);

		assertEquals(1, first.id());
		assertEquals(2, second.id());
	}
}
