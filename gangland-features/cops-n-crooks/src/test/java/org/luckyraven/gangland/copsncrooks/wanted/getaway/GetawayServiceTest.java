package org.luckyraven.gangland.copsncrooks.wanted.getaway;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.GetawaySettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService.Reason;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService.Result;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P-GET-1..8 at the service level: who is paid, how much, when, and the crew's half. */
@DisplayName("GetawayService - clean getaway XP")
class GetawayServiceTest {

	private static final int OWN_GANG   = 1;
	private static final int ALLY_GANG  = 2;
	private static final int OTHER_GANG = 3;

	private static final long T0 = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli();

	private final Map<UUID, Integer> gangOf = new HashMap<>();
	private final List<Player>       online = new ArrayList<>();
	private final List<Object[]>     grants = new ArrayList<>();
	private final List<UUID>         downed = new ArrayList<>();

	private AtomicLong        clock;
	private ChaseArcs         arcs;
	private ChaseConfigLoader config;
	private DetainmentService detainment;
	private WantedMessages    messages;
	private GangMembership    gangs;
	private Player            steve;
	private GetawayService    service;

	private MockedStatic<NpcSupport> npcSupport;

	@BeforeEach
	void setUp() {
		clock      = new AtomicLong(T0);
		arcs       = new ChaseArcs(clock::get);
		config     = mock(ChaseConfigLoader.class);
		detainment = mock(DetainmentService.class);
		messages   = mock(WantedMessages.class);
		when(messages.format(eq(WantedMessages.Key.GETAWAY_SHARE), anyMap())).thenReturn("share-line");
		settings(GetawaySettings.DEFAULT);

		gangs = new GangMembership();
		gangs.install(new GangMembershipView() {
			@Override
			public int gangIdOf(UUID uuid) {
				return gangOf.getOrDefault(uuid, -1);
			}

			@Override
			public boolean gangsAllied(int a, int b) {
				return (a == OWN_GANG && b == ALLY_GANG) || (a == ALLY_GANG && b == OWN_GANG);
			}

			@Override
			public Optional<String> nameOf(int gangId) {
				return Optional.empty();
			}
		});

		steve = player("Steve", OWN_GANG);
		online.add(steve);

		npcSupport = Mockito.mockStatic(NpcSupport.class);
		npcSupport.when(() -> NpcSupport.isNpc(any())).thenReturn(false);

		service = build(ZoneId.of("UTC"));
	}

	@AfterEach
	void tearDown() {
		npcSupport.close();
		downed.forEach(DownedPlayerRegistry::remove);
	}

	private GetawayService build(ZoneId zone) {
		return new GetawayService(arcs, config, gangs, detainment, messages, () -> online,
		                          (player, xp) -> grants.add(new Object[]{player, xp}), zone);
	}

	private void settings(GetawaySettings s) {
		when(config.getGetaway()).thenReturn(s);
	}

	private void settings(int cooldown, int cap) {
		settings(new GetawaySettings(true, List.of(0, 25, 75, 175, 350), cooldown, cap, Set.of()));
	}

	private Player player(String name, int gang) {
		Player p  = mock(Player.class);
		UUID   id = UUID.randomUUID();
		when(p.getUniqueId()).thenReturn(id);
		when(p.getName()).thenReturn(name);
		gangOf.put(id, gang);
		return p;
	}

	/** A chase with {@code level} earned stars (CRIME start) that the squad has seen. */
	private void chase(Player p, int level) {
		arcs.start(p.getUniqueId(), WantedCause.CRIME, level);
		arcs.seen(p.getUniqueId());
	}

	private Result award(Player p, WantedCause cause) {
		return service.award(p, arcs.arc(p.getUniqueId()), cause);
	}

	private int totalFor(Player p) {
		return grants.stream().filter(g -> g[0] == p).mapToInt(g -> (Integer) g[1]).sum();
	}

	private List<Integer> grantsFor(Player p) {
		return grants.stream().filter(g -> g[0] == p).map(g -> (Integer) g[1]).toList();
	}

	@Test
	@DisplayName("a seen 3-star evasion pays 75 and grants it once")
	void evasion3Stars_pays75() {
		chase(steve, 3);

		Result r = award(steve, WantedCause.EVASION);

		assertEquals(new Result(75, Reason.PAID), r);
		assertEquals(List.of(75), grantsFor(steve));
	}

	@Test
	@DisplayName("only EVASION pays: every other end cause is NOT_EVASION, no grant, no share")
	void onlyEvasionPays() {
		Player mate = player("Alex", OWN_GANG);
		online.add(mate);
		chase(steve, 3);
		chase(mate, 2);

		for (WantedCause cause : WantedCause.values()) {
			if (cause == WantedCause.EVASION) continue;
			assertEquals(new Result(0, Reason.NOT_EVASION), award(steve, cause), cause.name());
		}

		assertTrue(grants.isEmpty());
	}

	@Test
	@DisplayName("one earned star pays nothing and starts no cooldown")
	void oneStarPaysNothing() {
		chase(steve, 1);
		assertEquals(new Result(0, Reason.LOW_PEAK), award(steve, WantedCause.EVASION));

		arcs.start(steve.getUniqueId(), WantedCause.CRIME, 3);
		arcs.seen(steve.getUniqueId());
		assertEquals(new Result(75, Reason.PAID), award(steve, WantedCause.EVASION));
	}

	@Test
	@DisplayName("sign stars never earn: a 5-star sign chase is LOW_PEAK")
	void signStarsPayNothing() {
		arcs.start(steve.getUniqueId(), WantedCause.SIGN, 5);
		arcs.seen(steve.getUniqueId());

		assertEquals(new Result(0, Reason.LOW_PEAK), award(steve, WantedCause.EVASION));
		assertTrue(grants.isEmpty());
	}

	@Test
	@DisplayName("never seen by a cop: NO_SEARCH, nothing granted, no cooldown")
	void neverSeen_noSearch() {
		arcs.start(steve.getUniqueId(), WantedCause.CRIME, 3);
		arcs.lost(steve.getUniqueId());

		assertEquals(new Result(0, Reason.NO_SEARCH), award(steve, WantedCause.EVASION));
		assertTrue(grants.isEmpty());

		arcs.seen(steve.getUniqueId());
		assertEquals(new Result(75, Reason.PAID), award(steve, WantedCause.EVASION));
	}

	@Test
	@DisplayName("a second qualifying escape inside the cooldown pays and shares nothing; at the boundary it pays")
	void cooldown_tenMinutes() {
		Player mate = player("Alex", OWN_GANG);
		online.add(mate);
		chase(steve, 3);
		chase(mate, 2);
		assertEquals(Reason.PAID, award(steve, WantedCause.EVASION).reason());
		assertEquals(List.of(37), grantsFor(mate));

		clock.set(T0 + 599_000L);
		assertEquals(new Result(0, Reason.COOLDOWN), award(steve, WantedCause.EVASION));
		assertEquals(List.of(37), grantsFor(mate));

		clock.set(T0 + 600_000L);
		assertEquals(new Result(75, Reason.PAID), award(steve, WantedCause.EVASION));
	}

	@Test
	@DisplayName("the daily cap clips the award, then reads CAPPED with nothing granted")
	void dailyCap_clips() {
		settings(600, 100);
		chase(steve, 3);

		assertEquals(new Result(75, Reason.PAID), award(steve, WantedCause.EVASION));
		clock.addAndGet(600_000L);
		assertEquals(new Result(25, Reason.PAID), award(steve, WantedCause.EVASION));
		clock.addAndGet(600_000L);
		assertEquals(new Result(0, Reason.CAPPED), award(steve, WantedCause.EVASION));
		assertEquals(List.of(75, 25), grantsFor(steve));
	}

	@Test
	@DisplayName("the tally resets on the next local day of the injected zone")
	void dailyCap_resetsNextLocalDay() {
		settings(0, 75);
		clock.set(Instant.parse("2026-10-20T23:59:00Z").toEpochMilli());
		chase(steve, 3);

		assertEquals(new Result(75, Reason.PAID), award(steve, WantedCause.EVASION));
		clock.set(Instant.parse("2026-10-20T23:59:30Z").toEpochMilli());
		assertEquals(new Result(0, Reason.CAPPED), award(steve, WantedCause.EVASION));
		clock.set(Instant.parse("2026-10-21T00:01:00Z").toEpochMilli());
		assertEquals(new Result(75, Reason.PAID), award(steve, WantedCause.EVASION));
	}

	@Test
	@DisplayName("the day boundary follows the zone, not UTC")
	void dailyCap_dayRollUsesZone() {
		settings(0, 75);
		service = build(ZoneId.of("Asia/Tokyo"));
		// 14:59 UTC = 23:59 Tokyo; 15:01 UTC = 00:01 Tokyo next day
		clock.set(Instant.parse("2026-10-20T14:59:00Z").toEpochMilli());
		chase(steve, 3);

		assertEquals(Reason.PAID, award(steve, WantedCause.EVASION).reason());
		clock.set(Instant.parse("2026-10-20T15:01:00Z").toEpochMilli());
		assertEquals(new Result(75, Reason.PAID), award(steve, WantedCause.EVASION));
	}

	@Test
	@DisplayName("Daily_Cap 0 means no cap")
	void dailyCapZero_meansNoCap() {
		settings(0, 0);
		chase(steve, 5);

		for (int i = 0; i < 5; i++) {
			assertEquals(new Result(350, Reason.PAID), award(steve, WantedCause.EVASION));
		}
		assertEquals(1750, totalFor(steve));
	}

	@Test
	@DisplayName("a wanted gang mate (2 earned stars) gets floor(base / 2) and one message naming the escaper")
	@SuppressWarnings("unchecked")
	void share_halfToWantedMate() {
		Player mate = player("Alex", OWN_GANG);
		online.add(mate);
		chase(steve, 3);
		chase(mate, 2);

		award(steve, WantedCause.EVASION);

		assertEquals(List.of(37), grantsFor(mate));
		ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
		verify(messages).format(eq(WantedMessages.Key.GETAWAY_SHARE), values.capture());
		assertEquals("Steve", values.getValue().get("player"));
		assertEquals("37", values.getValue().get("xp"));
		verify(mate).sendMessage("share-line");
	}

	@Test
	@DisplayName("an allied gang's wanted member shares too; a stranger with an arc does not")
	void share_alliedYes_strangerNo() {
		Player ally     = player("Ally", ALLY_GANG);
		Player stranger = player("Stranger", OTHER_GANG);
		online.add(ally);
		online.add(stranger);
		chase(steve, 3);
		chase(ally, 1);
		chase(stranger, 3);

		award(steve, WantedCause.EVASION);

		assertEquals(List.of(37), grantsFor(ally));
		assertTrue(grantsFor(stranger).isEmpty());
		verify(stranger, never()).sendMessage("share-line");
	}

	@Test
	@DisplayName("an unwanted friend gets nothing")
	void share_notToUnwantedFriend() {
		Player friend = player("Friend", OWN_GANG);
		online.add(friend);
		chase(steve, 3);

		award(steve, WantedCause.EVASION);

		assertTrue(grantsFor(friend).isEmpty());
		verify(friend, never()).sendMessage("share-line");
	}

	@Test
	@DisplayName("a mate whose stars are all sign stars (earned 0) gets nothing")
	void share_notToSignWantedMate() {
		Player mate = player("Alex", OWN_GANG);
		online.add(mate);
		chase(steve, 3);
		arcs.start(mate.getUniqueId(), WantedCause.SIGN, 4);

		award(steve, WantedCause.EVASION);

		assertTrue(grantsFor(mate).isEmpty());
	}

	@Test
	@DisplayName("cuffed, downed and NPC mates are skipped")
	void share_skipsCuffedDownedNpc() {
		Player cuffed = player("Cuffed", OWN_GANG);
		Player hurt   = player("Hurt", OWN_GANG);
		Player npc    = player("Npc", OWN_GANG);
		online.addAll(List.of(cuffed, hurt, npc));
		chase(steve, 3);
		chase(cuffed, 2);
		chase(hurt, 2);
		chase(npc, 2);
		when(detainment.isRestrained(cuffed)).thenReturn(true);
		DownedPlayerRegistry.add(hurt.getUniqueId());
		downed.add(hurt.getUniqueId());
		npcSupport.when(() -> NpcSupport.isNpc(npc)).thenReturn(true);

		award(steve, WantedCause.EVASION);

		assertTrue(grants.stream().noneMatch(g -> g[0] != steve), "no mate was paid");
		assertEquals(List.of(75), grantsFor(steve));
	}

	@Test
	@DisplayName("the escaper himself is never paid a share on top of his award")
	void share_neverToEscaper() {
		chase(steve, 3);

		award(steve, WantedCause.EVASION);

		assertEquals(List.of(75), grantsFor(steve));
	}

	@Test
	@DisplayName("shares count toward the mate's daily cap and clip to it")
	void share_clippedByMateCap() {
		settings(600, 50);
		Player mate = player("Alex", OWN_GANG);
		online.add(mate);
		chase(steve, 3);
		chase(mate, 2);

		award(steve, WantedCause.EVASION);
		clock.addAndGet(600_000L);
		award(steve, WantedCause.EVASION);

		assertEquals(List.of(37, 13), grantsFor(mate));
	}

	@Test
	@DisplayName("a share never starts the mate's own cooldown")
	void share_doesNotStartMateCooldown() {
		Player mate = player("Alex", OWN_GANG);
		online.add(mate);
		chase(steve, 3);
		chase(mate, 2);

		award(steve, WantedCause.EVASION);

		assertEquals(new Result(25, Reason.PAID), award(mate, WantedCause.EVASION));
	}

	@Test
	@DisplayName("the crew still gets half of the un-capped base when the escaper's own cap is spent")
	void share_stillPaid_whenEscaperCapped() {
		settings(0, 75);
		Player mate = player("Alex", OWN_GANG);
		online.add(mate);
		chase(steve, 3);
		chase(mate, 3);

		award(steve, WantedCause.EVASION);
		Result r = award(steve, WantedCause.EVASION);

		assertEquals(new Result(0, Reason.CAPPED), r);
		assertEquals(List.of(37, 37), grantsFor(mate));
	}

	@Test
	@DisplayName("Enable false pays and shares nothing")
	void disabled() {
		settings(new GetawaySettings(false, List.of(0, 25, 75, 175, 350), 600, 1000, Set.of()));
		Player mate = player("Alex", OWN_GANG);
		online.add(mate);
		chase(steve, 3);
		chase(mate, 2);

		assertEquals(new Result(0, Reason.DISABLED), award(steve, WantedCause.EVASION));
		assertTrue(grants.isEmpty());
	}

	@Test
	@DisplayName("a peak above the list uses the last entry")
	void peakAboveList_usesLastEntry() {
		chase(steve, 5);

		assertEquals(new Result(350, Reason.PAID), award(steve, WantedCause.EVASION));
	}
}
