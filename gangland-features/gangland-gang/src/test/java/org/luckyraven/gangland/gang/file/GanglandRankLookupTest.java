package org.luckyraven.gangland.gang.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.datastructure.Tree;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * GR-28: an empty rank tree (no root) must not NPE the root-rank lookup; it falls back to the tail rank.
 */
@DisplayName("GanglandRankLookup - root rank fallback")
class GanglandRankLookupTest {

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
	}

	@Test
	@DisplayName("getRootRank with no tree root falls back to the tail rank and does not throw")
	void getRootRank_noRoot_fallsBackToTail_noNpe() {
		RankManager manager = mock(RankManager.class);
		Rank        tail    = mock(Rank.class);

		when(manager.getRankTree()).thenReturn(new Tree<>());
		when(manager.get(GangSettings.getGangRankTail())).thenReturn(tail);

		GanglandRankLookup lookup = new GanglandRankLookup(manager);

		assertSame(tail, lookup.getRootRank());
	}

	@Test
	@DisplayName("getRootRank with no tree root and no tail rank returns null instead of throwing")
	void getRootRank_noRootNoTail_returnsNull() {
		RankManager manager = mock(RankManager.class);

		when(manager.getRankTree()).thenReturn(new Tree<>());

		assertNull(new GanglandRankLookup(manager).getRootRank());
	}

}
