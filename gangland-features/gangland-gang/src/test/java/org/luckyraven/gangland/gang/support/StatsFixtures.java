package org.luckyraven.gangland.gang.support;

import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.stats.GangStatContribution;
import org.luckyraven.gangland.gang.stats.GangStatContributions;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.GangSettings;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Gangs, members and a refreshed {@link GangStatsService} for the stats, ranking and placeholder tests. */
public final class StatsFixtures {

	private StatsFixtures() {
	}

	/** A member with the given gang counters. */
	public static Member member(int gangId, int kills, int deaths, int assists) {
		Member member = new Member(UUID.randomUUID());
		member.setGangId(gangId);
		member.setKills(kills);
		member.setDeaths(deaths);
		member.setAssists(assists);
		return member;
	}

	/** A real gang (level mocked) with the given members, level and bank balance. */
	public static Gang gang(int id, String name, int level, double balance, Member... members) {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());

		Gang  gang = spy(new Gang(id, new ArrayList<>(List.of(members)), name));
		Level lvl  = mock(Level.class);
		when(lvl.getLevelValue()).thenReturn(level);
		doReturn(lvl).when(gang).getLevel();
		gang.getEconomy().setAmount(BigDecimal.valueOf(balance));
		return gang;
	}

	/** A service over {@code gangs}, already refreshed once; {@code wallet} is every member's money. */
	public static GangStatsService service(int minKills, double wallet, List<GangStatContribution> extras,
	                                       Gang... gangs) {
		GangManager manager = mock(GangManager.class);
		Collection<Gang> all = List.of(gangs);
		when(manager.getAll()).thenReturn(all);
		GangRules rules = mock(GangRules.class);
		when(rules.getRankingMinKills()).thenReturn(minKills);
		when(rules.getRankingPageSize()).thenReturn(2);

		GangStatsService service = new GangStatsService(null, manager, rules, () -> new GangStatContributions(extras),
		                                                uuid -> wallet, id -> 1);
		service.refresh();
		return service;
	}

	public static GangStatContribution contribution(String key, long perGang) {
		return new GangStatContribution() {
			@Override
			public String key() {
				return key;
			}

			@Override
			public long value(int gangId) {
				return perGang * gangId;
			}
		};
	}
}
