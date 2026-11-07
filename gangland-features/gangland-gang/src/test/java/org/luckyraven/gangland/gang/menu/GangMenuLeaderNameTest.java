package org.luckyraven.gangland.gang.menu;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangFilterAdapter;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberFilterAdapter;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.menu.filter.FilterApplier;
import org.luckyraven.gangland.menu.filter.FilterRegistry;
import org.luckyraven.gangland.menu.filter.FilterStore;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.luckyraven.gangland.gang.support.StatsFixtures.gang;
import static org.luckyraven.gangland.gang.support.StatsFixtures.member;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Characterises the {@code gang_leader_name} row of the {@code gangs} menu source: per gang, the first member whose rank
 * name equals the tail rank ignoring case (a rank-less member is passed over), by offline-player name; a leader with no
 * known name, or a gang with no leader, shows an empty string, and the scan never moves on to a second tail-rank member.
 */
@DisplayName("GangMenuItemSourceContribution - the gang_leader_name of the gangs source")
class GangMenuLeaderNameTest {

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
	@DisplayName("each gang answers its own first tail-rank member, in gang order")
	void leaderNamePerGang() {
		Gang found     = gangOf(1, new String[]{null, "member", "OWNER", "owner"}, new String[]{"A", "B", "Boss", "Second"});
		Gang leaderless = gangOf(2, new String[]{null, "member"}, new String[]{"C", "D"});
		Gang unnamed   = gangOf(3, new String[]{"owner", "owner"}, new String[]{null, "Second"});

		GangManager gangs = mock(GangManager.class);
		when(gangs.getGangs()).thenReturn(new LinkedHashMap<>(Map.of(1, found, 2, leaderless, 3, unnamed)));

		GangMenuItemSourceContribution contribution = new GangMenuItemSourceContribution(
				mock(UserManager.class), gangs, new FilterStore(new FilterRegistry()), new FilterApplier(),
				new GangFilterAdapter(), new MemberFilterAdapter(), mock(GangStatsService.class));

		List<Map<String, String>> entries = contribution.entries(mock(Player.class), "gangs");

		Map<String, String> byId = new LinkedHashMap<>();
		for (Map<String, String> entry : entries) {
			byId.put(entry.get("gang_id"), entry.get("gang_leader_name"));
		}
		assertEquals(Map.of("1", "Boss", "2", "", "3", ""), byId);
		assertEquals(3, entries.size());
	}

	private Gang gangOf(int id, String[] rankNames, String[] playerNames) {
		List<Member> members = new ArrayList<>();
		for (int i = 0; i < rankNames.length; i++) {
			Member member = member(id, 0, 0, 0);
			if (rankNames[i] != null) {
				Rank rank = mock(Rank.class);
				when(rank.getName()).thenReturn(rankNames[i]);
				member.setRank(rank);
			}
			members.add(member);

			OfflinePlayer named = mock(OfflinePlayer.class);
			when(named.getName()).thenReturn(playerNames[i]);
			UUID uuid = member.getUuid();
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(named);
		}
		return gang(id, "Gang" + id, 1, 0, members.toArray(new Member[0]));
	}
}
