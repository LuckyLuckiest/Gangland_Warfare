package org.luckyraven.gangland.gang.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.stats.GangStatsService.Row;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.gang.support.StatsFixtures.contribution;
import static org.luckyraven.gangland.gang.support.StatsFixtures.gang;
import static org.luckyraven.gangland.gang.support.StatsFixtures.member;
import static org.luckyraven.gangland.gang.support.StatsFixtures.service;

@DisplayName("GangStatsService - the shared ranking snapshot")
class GangStatsServiceTest {

	private static List<Integer> ids(List<Row> rows) {
		return rows.stream().map(Row::gangId).toList();
	}

	private static Gang[] three() {
		Gang a = gang(1, "Alpha", 3, 100, member(1, 10, 5, 2), member(1, 10, 5, 1));
		Gang b = gang(2, "Bravo", 5, 900, member(2, 40, 8, 9));
		Gang c = gang(3, "Charlie", 1, 50, member(3, 5, 0, 0), member(3, 5, 0, 0), member(3, 1, 0, 0));
		return new Gang[]{a, b, c};
	}

	@Test
	@DisplayName("eachSort: every stat orders biggest first")
	void eachSort() {
		GangStatsService service = service(0, 7, List.of(contribution("turfs", 2), contribution("wins", 1)), three());

		assertEquals(List.of(2, 1, 3), ids(service.ranking("kills", true)));
		assertEquals(List.of(3, 2, 1), ids(service.ranking("kdr", true)));
		assertEquals(List.of(2, 1, 3), ids(service.ranking("assists", true)));
		assertEquals(List.of(2, 1, 3), ids(service.ranking("level", true)));
		assertEquals(List.of(3, 1, 2), ids(service.ranking("members", true)));
		assertEquals(List.of(3, 2, 1), ids(service.ranking("turfs", true)));
		assertEquals(List.of(3, 2, 1), ids(service.ranking("wins", true)));
		assertEquals(List.of(2, 1, 3), ids(service.ranking("balance", true)));
		assertEquals(List.of(3, 1, 2), ids(service.ranking("money", true)));
		assertEquals(14.0, service.snapshot().row(1).orElseThrow().membersMoney(), 0.001);
	}

	@Test
	@DisplayName("tiesById: equal values keep the lower gang id first")
	void tiesById() {
		Gang a = gang(4, "Delta", 2, 0, member(4, 3, 1, 0));
		Gang b = gang(2, "Bravo", 2, 0, member(2, 3, 1, 0));
		Gang c = gang(9, "India", 2, 0, member(9, 3, 1, 0));
		GangStatsService service = service(0, 0, List.of(), a, b, c);

		assertEquals(List.of(2, 4, 9), ids(service.ranking("kills", true)));
		assertEquals(List.of(2, 4, 9), ids(service.ranking("level", true)));
	}

	@Test
	@DisplayName("minKillsFloor: a gang below Ranking.Min_Kills kills is left out of the kdr ranking only")
	void minKillsFloor() {
		GangStatsService service = service(12, 0, List.of(), three());

		// Charlie has 11 kills: out of kdr, still in every other ranking
		assertEquals(List.of(2, 1), ids(service.ranking("kdr", true)));
		assertEquals(List.of(2, 1, 3), ids(service.ranking("kills", true)));
		assertEquals(List.of(3, 2, 1), ids(service(11, 0, List.of(), three()).ranking("kdr", true)));
	}

	@Test
	@DisplayName("wealthHiddenWithoutNode: balance and money rank only for an asker with the wealth node")
	void wealthHiddenWithoutNode() {
		GangStatsService service = service(0, 5, List.of(), three());

		assertTrue(service.isWealth("balance"));
		assertTrue(service.isWealth("money"));
		assertTrue(service.ranking("balance", false).isEmpty());
		assertTrue(service.ranking("money", false).isEmpty());
		assertEquals(3, service.ranking("balance", true).size());
		assertEquals(3, service.ranking("kills", false).size());
	}

	@Test
	@DisplayName("snapshotImmutable: the snapshot and its ranking cannot be edited, and a later change shows only in the next one")
	void snapshotImmutable() {
		Member bravoMember = member(2, 40, 8, 9);
		Gang   bravo       = gang(2, "Bravo", 5, 900, bravoMember);
		GangStatsService service = service(0, 0, List.of(), bravo);

		GangStatsService.Snapshot before = service.snapshot();
		assertThrows(UnsupportedOperationException.class, () -> before.rows().add(before.rows().get(0)));
		assertThrows(UnsupportedOperationException.class, () -> before.ranked("kills").clear());
		assertThrows(UnsupportedOperationException.class, () -> before.stats().add("x"));

		bravoMember.setKills(41);
		assertEquals(40, service.snapshot().row(2).orElseThrow().kills());
		service.refresh();
		assertEquals(41, service.snapshot().row(2).orElseThrow().kills());
		assertEquals(40, before.row(2).orElseThrow().kills());
	}

	@Test
	@DisplayName("contributionFakes: a contributed key becomes a rankable stat with the module's numbers")
	void contributionFakes() {
		GangStatsService service = service(0, 0, List.of(contribution("wlr", 3)), three());

		assertTrue(service.statKeys().contains("wlr"));
		assertEquals(List.of(3, 2, 1), ids(service.ranking("wlr", true)));
		assertEquals(9L, service.snapshot().row(3).orElseThrow().extra().get("wlr"));
		// the module stats always exist, as 0 when nobody contributes them
		assertEquals(0L, service.snapshot().row(1).orElseThrow().extra().get("turfs"));
	}

	@Test
	@DisplayName("resetPlayer zeroes one member's counters")
	void resetPlayer() {
		Member target = member(1, 4, 3, 2);
		Member other  = member(1, 9, 9, 9);
		Gang   gang   = gang(1, "Alpha", 1, 0, target, other);
		org.luckyraven.gangland.gang.GangManager manager = org.mockito.Mockito.mock(
				org.luckyraven.gangland.gang.GangManager.class);
		org.mockito.Mockito.when(manager.getAll()).thenReturn(List.of(gang));

		new GangStatsService(null, manager, null, null, null, null).resetPlayer(target.getUuid());

		assertEquals(0, target.getKills() + target.getDeaths() + target.getAssists());
		assertEquals(9, other.getKills());
	}
}
