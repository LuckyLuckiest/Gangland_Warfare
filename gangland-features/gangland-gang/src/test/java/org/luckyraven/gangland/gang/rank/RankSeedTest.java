package org.luckyraven.gangland.gang.rank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.database.repositories.rank.RankParentRepository;
import org.luckyraven.gangland.gang.database.repositories.rank.RankRepository;
import org.luckyraven.gangland.gang.file.GangRules;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Pins {@link RankSeed#seedIfEmpty}: the preset ladder is written only on an empty rank table (D13 A), lowest rank
 * first with ids 1..n, each rank linked to the next through {@code insertInitialRelation(parent, child)}.
 */
@DisplayName("RankSeed - preset ladder on an empty rank table only")
class RankSeedTest {

	private final RankRepository       ranks   = mock(RankRepository.class);
	private final RankParentRepository parents = mock(RankParentRepository.class);
	private final GangRules            rules   = mock(GangRules.class);

	@Test
	@DisplayName("an empty table with the default preset seeds member, officer, owner and two links")
	void emptyTable_defaultPreset_seedsThreeRanksAndTwoLinks() throws Exception {
		when(ranks.totalRanks()).thenReturn(0);
		when(rules.getRankPreset()).thenReturn(List.of("member", "officer", "owner"));

		assertTrue(RankSeed.seedIfEmpty(ranks, parents, rules, "member", "owner"));

		ArgumentCaptor<Rank> saved = ArgumentCaptor.forClass(Rank.class);
		verify(ranks, times(3)).insertNow(saved.capture());
		assertEquals(List.of("member", "officer", "owner"), saved.getAllValues().stream().map(Rank::getName).toList());
		assertEquals(List.of(1, 2, 3), saved.getAllValues().stream().map(Rank::getUsedId).toList());
		verify(parents).insertInitialRelation(1, 2);
		verify(parents).insertInitialRelation(2, 3);
	}

	@Test
	@DisplayName("a non-empty table is left alone: nothing is inserted and false is returned")
	void nonEmptyTable_seedsNothing() throws Exception {
		when(ranks.totalRanks()).thenReturn(4);

		assertFalse(RankSeed.seedIfEmpty(ranks, parents, rules, "member", "owner"));

		verify(ranks, never()).insertNow(any(Rank.class));
		verify(parents, never()).insertInitialRelation(anyInt(), anyInt());
	}

	@Test
	@DisplayName("a five-rung preset seeds five ranks in order and four links")
	void fiveRungPreset_seedsFiveRanksInOrder() throws Exception {
		when(ranks.totalRanks()).thenReturn(0);
		when(rules.getRankPreset()).thenReturn(List.of("member", "soldier", "officer", "underboss", "owner"));

		RankSeed.seedIfEmpty(ranks, parents, rules, "member", "owner");

		ArgumentCaptor<Rank> saved = ArgumentCaptor.forClass(Rank.class);
		verify(ranks, times(5)).insertNow(saved.capture());
		assertEquals(List.of("member", "soldier", "officer", "underboss", "owner"),
		             saved.getAllValues().stream().map(Rank::getName).toList());
		verify(parents).insertInitialRelation(1, 2);
		verify(parents).insertInitialRelation(2, 3);
		verify(parents).insertInitialRelation(3, 4);
		verify(parents).insertInitialRelation(4, 5);
	}

	@Test
	@DisplayName("a preset whose ends disagree with Rank.Head and Rank.Tail seeds the configured names at the ends")
	void presetEndsMismatch_usesHeadAndTailAtTheEnds() throws Exception {
		when(ranks.totalRanks()).thenReturn(0);
		when(rules.getRankPreset()).thenReturn(List.of("soldier", "officer", "boss"));

		RankSeed.seedIfEmpty(ranks, parents, rules, "member", "owner");

		ArgumentCaptor<Rank> saved = ArgumentCaptor.forClass(Rank.class);
		verify(ranks, times(3)).insertNow(saved.capture());
		assertEquals(List.of("member", "officer", "owner"), saved.getAllValues().stream().map(Rank::getName).toList());
	}

}
