package org.luckyraven.gangland.gang.membership;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises {@link MembershipQueries#ownerOf(int)}: the first member whose rank name equals the tail rank ignoring
 * case, a member without a rank passed over, empty when nobody holds it or the gang is unknown.
 */
@DisplayName("MembershipQueries - ownerOf")
class MembershipQueriesOwnerTest {

	private GangManager       gangManager;
	private MembershipQueries queries;

	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());
		gangManager = mock(GangManager.class);
		queries     = new MembershipQueries(gangManager);
	}

	@Test
	@DisplayName("a rank-less member is passed over, the tail rank is matched ignoring case, the first of two wins")
	void firstTailRankMemberWins() {
		Member first  = member(null);
		Member second = member("member");
		Member third  = member("OWNER");
		Member fourth = member("owner");
		gang(1, first, second, third, fourth);

		assertEquals(Optional.of(third.getUuid()), queries.ownerOf(1));
	}

	@Test
	@DisplayName("nobody on the tail rank answers empty")
	void nobodyOnTailRank_empty() {
		gang(1, member(null), member("member"), member("officer"));

		assertEquals(Optional.empty(), queries.ownerOf(1));
	}

	@Test
	@DisplayName("an unknown gang answers empty")
	void unknownGang_empty() {
		assertEquals(Optional.empty(), queries.ownerOf(9));
	}

	private void gang(int id, Member... members) {
		Gang gang = mock(Gang.class);
		when(gang.getMembers()).thenReturn(new ArrayList<>(List.of(members)));
		when(gangManager.getGang(id)).thenReturn(gang);
	}

	private static Member member(String rankName) {
		Member member = new Member(UUID.randomUUID());
		if (rankName != null) {
			Rank rank = mock(Rank.class);
			when(rank.getName()).thenReturn(rankName);
			member.setRank(rank);
		}
		return member;
	}
}
