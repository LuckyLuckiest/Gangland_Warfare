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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.gang.support.StatsFixtures.gang;
import static org.luckyraven.gangland.gang.support.StatsFixtures.member;
import static org.luckyraven.gangland.gang.support.StatsFixtures.service;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("GangPlaceholderContribution - the gang parity tokens and board rows")
class GangPlaceholderContributionTest {

	private MockedStatic<Bukkit>        bukkit;
	private GangPlaceholderContribution contribution;
	private OfflinePlayer               inGang;
	private OfflinePlayer               loner;

	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());
		bukkit = mockStatic(Bukkit.class);

		Rank leaderRank = mock(Rank.class);
		when(leaderRank.getName()).thenReturn("owner");
		Member leader = member(1, 20, 5, 4);
		leader.setRank(leaderRank);
		Member recruit = member(1, 0, 0, 0);

		Gang alpha = gang(1, "Alpha", 3, 100, leader, recruit);
		alpha.setNotice("Meet at dawn");
		alpha.setFriendlyFire(true);
		Gang bravo = gang(2, "Bravo", 5, 900, member(2, 40, 8, 9));
		Gang charlie = gang(3, "Charlie", 1, 50, member(3, 11, 0, 0));
		GangStatsService stats = service(0, 10, List.of(), alpha, bravo, charlie);

		OfflinePlayer boss   = named("Boss");
		OfflinePlayer rookie = named("Rookie");
		bukkit.when(() -> Bukkit.getOfflinePlayer(leader.getUuid())).thenReturn(boss);
		bukkit.when(() -> Bukkit.getOfflinePlayer(recruit.getUuid())).thenReturn(rookie);

		GangManager gangs = mock(GangManager.class);
		when(gangs.getGang(1)).thenReturn(alpha);
		MemberManager members = mock(MemberManager.class);
		UUID lonerId = UUID.randomUUID();
		inGang = player(leader.getUuid());
		loner  = player(lonerId);
		Member lonerMember = new Member(lonerId);
		when(members.getMember(leader.getUuid())).thenReturn(leader);
		when(members.getMember(lonerId)).thenReturn(lonerMember);

		GangRules rules = mock(GangRules.class);
		when(rules.getPlaceholdersDefaultValue()).thenReturn("none");
		when(rules.getRankPreset()).thenReturn(List.of("member", "officer", "owner"));

		GangSafehouseService safehouses = mock(GangSafehouseService.class);
		when(safehouses.count(1)).thenReturn(2);
		GangLimits limits = mock(GangLimits.class);
		when(limits.maxMembers(1)).thenReturn(8);
		when(limits.maxAllies(1)).thenReturn(0);
		when(limits.upgradeCost(1)).thenReturn(1500D);
		when(limits.chatPrefix(1)).thenReturn("[III]");

		contribution = new GangPlaceholderContribution(mock(UserManager.class), members, gangs, stats, safehouses, limits, rules);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private static OfflinePlayer named(String name) {
		OfflinePlayer player = mock(OfflinePlayer.class);
		when(player.getName()).thenReturn(name);
		return player;
	}

	private static OfflinePlayer player(UUID uuid) {
		OfflinePlayer player = mock(OfflinePlayer.class);
		when(player.getUniqueId()).thenReturn(uuid);
		return player;
	}

	@Test
	@DisplayName("everyToken: each parity token answers for a member")
	void everyToken() {
		Map<String, String> expected = Map.ofEntries(
				Map.entry("gang_friendly-fire", "true"),
				Map.entry("gang_leader", "Boss"),
				Map.entry("gang_member-list", "Boss, Rookie"),
				Map.entry("gang_kills", "20"),
				Map.entry("gang_deaths", "5"),
				Map.entry("gang_kdr", "4.00"),
				Map.entry("gang_assists", "4"),
				Map.entry("gang_safehouses", "2"),
				Map.entry("gang_notice", "Meet at dawn"),
				Map.entry("gang_member-cap", "8"),
				Map.entry("gang_ally-cap", "unlimited"),
				Map.entry("gang_level-prefix", "[III]"),
				Map.entry("user_assists", "4"),
				Map.entry("user_rank-position", "1"),
				Map.entry("gang_stat_kills", "20"),
				Map.entry("gang_stat_turfs", "0"));

		expected.forEach((token, value) -> assertEquals(value, contribution.resolve(inGang, token), token));
		for (String token : List.of("gang_members-money", "gang_upgrade-cost", "gang_upgrade-xp", "gang_online-list")) {
			assertTrue(contribution.resolve(inGang, token) != null, token);
		}
	}

	@Test
	@DisplayName("ganglessDefault: a player without a gang gets Placeholders.Default_Value")
	void ganglessDefault() {
		for (String token : List.of("gang_leader", "gang_kills", "gang_notice", "gang_member-cap", "user_assists",
		                            "user_rank-position", "gang_stat_wins")) {
			assertEquals("none", contribution.resolve(loner, token), token);
		}
	}

	@Test
	@DisplayName("boardToken_viaResolveGlobal_nullPlayer: rows read the same ranking as the chat command")
	void boardToken_viaResolveGlobal_nullPlayer() {
		assertEquals("Bravo", contribution.resolveGlobal("gang_board_kills_1_name"));
		assertEquals("40", contribution.resolveGlobal("gang_board_kills_1_value"));
		assertEquals("5", contribution.resolveGlobal("gang_board_kills_1_level"));
		assertEquals("Alpha", contribution.resolveGlobal("gang_board_kills_2_name"));
		assertEquals("5.00", contribution.resolveGlobal("gang_board_kdr_2_value"));
		assertEquals("none", contribution.resolveGlobal("gang_board_kills_9_name"));
		assertNull(contribution.resolveGlobal("gang_board_nonsense_1_name"));
		assertNull(contribution.resolveGlobal("gang_name"));
	}

	@Test
	@DisplayName("noBankOrUserSubstringInBoardTokens: no stat key makes a board token contain bank_ or user_")
	void noBankOrUserSubstringInBoardTokens() {
		GangStatsService stats = service(0, 0, List.of(), gang(1, "Alpha", 1, 0, member(1, 1, 0, 0)));

		for (String stat : stats.statKeys()) {
			String token = "gang_board_" + stat + "_1_name";
			assertTrue(!token.contains("bank_") && !token.contains("user_"), token);
		}
		assertTrue(!stats.statKeys().contains("bank"));
	}
}
