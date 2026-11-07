package org.luckyraven.gangland.gang.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.luckyraven.gangland.gang.support.StatsFixtures.gang;
import static org.luckyraven.gangland.gang.support.StatsFixtures.member;
import static org.luckyraven.gangland.gang.support.StatsFixtures.service;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Characterises the {@code gang_leader} token: the first member whose rank name equals the tail rank ignoring case
 * (a member without a rank is passed over), shown by offline-player name; a leader with no known name, or no leader at
 * all, answers the configured default, and the scan never moves on to a second tail-rank member.
 */
@DisplayName("GangPlaceholderContribution - the gang_leader token")
class GangPlaceholderLeaderTest {

	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());
		bukkit = mockStatic(Bukkit.class);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a rank-less member is passed over and the tail rank is matched ignoring case")
	void rankLessSkipped_tailMatchedIgnoringCase() {
		assertEquals("Boss", leaderToken(new String[]{null, "member", "OWNER", "owner"},
		                                 new String[]{"A", "B", "Boss", "Second"}));
	}

	@Test
	@DisplayName("no tail-rank member answers the default value")
	void noLeader_defaultValue() {
		assertEquals("none", leaderToken(new String[]{null, "member"}, new String[]{"A", "B"}));
	}

	@Test
	@DisplayName("the first tail-rank member with no known name answers the default, the scan does not go on")
	void firstLeaderWithoutName_defaultValue() {
		assertEquals("none", leaderToken(new String[]{"owner", "owner"}, new String[]{null, "Second"}));
	}

	private String leaderToken(String[] rankNames, String[] playerNames) {
		Member[] members = new Member[rankNames.length];
		for (int i = 0; i < members.length; i++) {
			members[i] = member(1, 0, 0, 0);
			if (rankNames[i] != null) {
				Rank rank = mock(Rank.class);
				when(rank.getName()).thenReturn(rankNames[i]);
				members[i].setRank(rank);
			}

			OfflinePlayer named = mock(OfflinePlayer.class);
			when(named.getName()).thenReturn(playerNames[i]);
			Member current = members[i];
			bukkit.when(() -> Bukkit.getOfflinePlayer(current.getUuid())).thenReturn(named);
		}

		Gang             alpha = gang(1, "Alpha", 1, 100, members);
		GangStatsService stats = service(0, 10, List.of(), alpha);

		GangManager gangs = mock(GangManager.class);
		when(gangs.getGang(1)).thenReturn(alpha);
		MemberManager memberManager = mock(MemberManager.class);
		when(memberManager.getMember(members[0].getUuid())).thenReturn(members[0]);

		GangRules rules = mock(GangRules.class);
		when(rules.getPlaceholdersDefaultValue()).thenReturn("none");
		when(rules.getRankPreset()).thenReturn(List.of("member", "officer", "owner"));

		GangPlaceholderContribution contribution = new GangPlaceholderContribution(
				mock(UserManager.class), memberManager, gangs, stats, mock(GangSafehouseService.class),
				mock(GangLimits.class), rules);

		OfflinePlayer asker = mock(OfflinePlayer.class);
		when(asker.getUniqueId()).thenReturn(members[0].getUuid());
		return contribution.resolve(asker, "gang_leader");
	}
}
