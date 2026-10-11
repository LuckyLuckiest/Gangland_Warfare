package org.luckyraven.gangland.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.gang.GangChatChannel;
import org.luckyraven.gangland.gang.chat.ChatChannels;
import org.luckyraven.gangland.gang.chat.ChatSpyService;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.combat.DamageLedger;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.combat.region.FriendlyFireRegionSource;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.MembershipQueries;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.stats.GangStatContribution;
import org.luckyraven.gangland.gang.stats.GangStatContributions;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The seam types lanes W5/W6 build on: their stub bodies answer the neutral value and never throw. */
@DisplayName("Gang seam stubs - neutral answers")
class GangSeamStubsTest {

	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();

	@Test
	@DisplayName("GangLimits: 0 means unlimited, no prefix")
	void limits() {
		GangLimits limits = new GangLimits();

		assertEquals(0, limits.maxMembers(1));
		assertEquals(0, limits.maxSafehouses(1));
		assertEquals(0, limits.maxAllies(1));
		assertEquals("", limits.chatPrefix(1));
		assertEquals(0D, limits.upgradeCost(1));
		assertEquals(0D, limits.upgradeXpMissing(1));
	}

	@Test
	@DisplayName("GangLevelTable: a zero row")
	void levelTable() {
		GangLevelTable.Row row = new GangLevelTable().row(3);

		assertEquals(3, row.level());
		assertEquals(0D, row.cost());
		assertTrue(row.commands().isEmpty());
	}

	@Test
	@DisplayName("MembershipQueries: empty for no gangs")
	void membershipQueries() {
		GangManager gangManager = mock(GangManager.class);
		MembershipQueries queries = new MembershipQueries(gangManager);

		assertTrue(queries.membersOf(1).isEmpty());
		assertTrue(queries.ownerOf(1).isEmpty());
		assertTrue(queries.gangIds().isEmpty());
		assertEquals(-1, queries.gangIdByName("Rats"));
	}

	@Test
	@DisplayName("FriendlyFirePolicy: today's rule, same gang or allied gangs are blocked")
	void friendlyFirePolicy() {
		MemberManager memberManager = mock(MemberManager.class);
		GangManager   gangManager   = mock(GangManager.class);
		Member        ma            = new Member(a);
		Member        mb            = new Member(b);
		ma.setGangId(1);
		mb.setGangId(1);
		when(memberManager.getMember(a)).thenReturn(ma);
		when(memberManager.getMember(b)).thenReturn(mb);

		org.luckyraven.gangland.gang.GangWorldPolicy worldPolicy = mock(org.luckyraven.gangland.gang.GangWorldPolicy.class);
		when(worldPolicy.enabledIn(any())).thenReturn(true);
		FriendlyFirePolicy policy = new FriendlyFirePolicy(memberManager, gangManager, mock(org.luckyraven.gangland.gang.file.GangRules.class), worldPolicy, FriendlyFireRegionSource.NONE, null);
		assertTrue(policy.pvpBlocked(a, b, null), "same gang");

		mb.setGangId(2);
		Gang one = new Gang(1);
		Gang two = new Gang(2);
		when(gangManager.getGang(1)).thenReturn(one);
		when(gangManager.getGang(2)).thenReturn(two);
		assertFalse(policy.pvpBlocked(a, b, null), "different, not allied");

		one.addAlly(two);
		assertTrue(policy.pvpBlocked(a, b, null), "allied");
		assertFalse(policy.pvpBlocked(a, UUID.randomUUID(), null), "gangless victim");
	}

	@Test
	@DisplayName("CombatTag: never tagged; the fighting flag is stored")
	void combatTag() {
		CombatTag tag = new CombatTag();
		tag.tag(a, b);

		assertFalse(tag.isTagged(b));
		assertEquals(Duration.ZERO, tag.remaining(b));
		assertFalse(tag.isFighting(a));
		tag.setFighting(a, true);
		assertTrue(tag.isFighting(a));
		tag.setFighting(a, false);
		assertFalse(tag.isFighting(a));
	}

	@Test
	@DisplayName("DamageLedger: empty")
	void damageLedger() {
		DamageLedger ledger = new DamageLedger();
		ledger.record(a, b, 3D, 0L);

		assertTrue(ledger.lastDamager(b).isEmpty());
		assertTrue(ledger.assistants(b, Duration.ofSeconds(10)).isEmpty());
	}

	@Test
	@DisplayName("FriendlyFireRegionSource.NONE has no opinion")
	void regionSource() {
		assertTrue(FriendlyFireRegionSource.NONE.allowed(null).isEmpty());
	}

	@Test
	@DisplayName("GangSafehouseService: none, teleport refused")
	void safehouseService() {
		GangSafehouseService service = new GangSafehouseService();

		assertEquals(0, service.count(1));
		assertTrue(service.find(1, "x").isEmpty());
		assertTrue(service.list(1).isEmpty());
		service.remove(1, "x");
	}

	@Test
	@DisplayName("GangStatsService: a snapshot object, reset does not throw")
	void statsService() {
		GangStatsService service = new GangStatsService();

		assertNotNull(service.snapshot());
		service.resetPlayer(a);
	}

	@Test
	@DisplayName("GangStatContributions.from collects the registered beans")
	void statContributions() {
		GangStatContribution turfs = new GangStatContribution() {
			@Override
			public String key() {
				return "turfs";
			}

			@Override
			public long value(int gangId) {
				return 4;
			}
		};
		DependencyContainer container = mock(DependencyContainer.class);
		when(container.getAllInstances(GangStatContribution.class)).thenReturn(List.of(turfs));

		assertEquals(List.of(turfs), GangStatContributions.from(container).all());
		assertTrue(GangStatContributions.from(mock(DependencyContainer.class)).all().isEmpty());
	}

	@Test
	@DisplayName("ChatSpyService toggles; ChatChannels follows the gang chat switch")
	void chat() {
		ChatSpyService spy = new ChatSpyService();
		assertTrue(spy.toggle(a));
		assertTrue(spy.spies().contains(a));
		assertFalse(spy.toggle(a));

		GangChatToggles toggles = new GangChatToggles();
		ChatChannels    channels = new ChatChannels(toggles);
		assertEquals(GangChatChannel.PUBLIC, channels.chatChannelOf(a));
		toggles.toggle(a);
		assertEquals(GangChatChannel.GANG, channels.chatChannelOf(a));
	}

}
