package org.luckyraven.gangland.listener.gang.stats;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.combat.DamageLedger;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GangStatsListener - which kills, deaths and assists count")
class GangStatsListenerTest {

	private static final long HOUR = 3_600_000L;
	private static final long NOW  = 1_000 * HOUR;

	/** A ledger the test fills by hand. */
	private static final class FakeLedger extends DamageLedger {

		UUID       last;
		List<UUID> helpers = new ArrayList<>();

		@Override
		public Optional<UUID> lastDamager(UUID victim) {
			return Optional.ofNullable(last);
		}

		@Override
		public List<UUID> assistants(UUID victim, Duration window) {
			return window.toSeconds() >= 10 ? helpers : List.of();
		}
	}

	private MemberManager   members;
	private GangManager     gangs;
	private GangWorldPolicy world;
	private GangRules       rules;
	private FakeLedger      ledger;
	private GangStatsListener listener;
	private long            now;

	private Player killerPlayer;
	private Player victimPlayer;
	private Member killer;
	private Member victim;
	private Gang   killerGang;
	private Gang   victimGang;

	@BeforeEach
	void setUp() {
		members = mock(MemberManager.class);
		gangs   = mock(GangManager.class);
		world   = mock(GangWorldPolicy.class);
		rules   = mock(GangRules.class);
		ledger  = new FakeLedger();
		when(world.enabledIn(any())).thenReturn(true);
		when(rules.getStatsAssistWindowSeconds()).thenReturn(10);
		when(rules.getStatsMinVictimMemberAgeHours()).thenReturn(24);
		when(rules.getStatsRepeatKillCooldownSeconds()).thenReturn(300);
		when(rules.getStatsMaxKillsPerVictimPerDay()).thenReturn(3);

		listener       = new GangStatsListener(members, gangs, ledger, world, rules);
		now            = NOW;
		listener.clock = () -> now;

		killerGang   = gang(1);
		victimGang   = gang(2);
		killerPlayer = player();
		victimPlayer = player();
		killer       = enroll(killerPlayer, 1, NOW - 100 * HOUR);
		victim       = enroll(victimPlayer, 2, NOW - 100 * HOUR);
		ledger.last  = killerPlayer.getUniqueId();
	}

	private Gang gang(int id) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		when(gangs.getGang(id)).thenReturn(gang);
		return gang;
	}

	private Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getWorld()).thenReturn(mock(World.class));
		return player;
	}

	private Member enroll(Player player, int gangId, long joined) {
		Member member = new Member(player.getUniqueId());
		member.setGangId(gangId);
		member.setGangJoinDateLong(joined);
		when(members.getMember(player.getUniqueId())).thenReturn(member);
		return member;
	}

	private void dies(Player who) {
		PlayerDeathEvent event = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(who);
		listener.onDeath(event);
	}

	@Test
	@DisplayName("a fair kill counts: killer kills, victim deaths")
	void fairKill_counts() {
		dies(victimPlayer);

		assertEquals(1, killer.getKills());
		assertEquals(1, victim.getDeaths());
		assertEquals(0, killer.getDeaths());
	}

	@Test
	@DisplayName("sameGangAndAlly_neverCount: gangmates and allied gangs score nothing")
	void sameGangAndAlly_neverCount() {
		victim.setGangId(1);
		dies(victimPlayer);
		assertEquals(0, killer.getKills());
		assertEquals(0, victim.getDeaths());

		victim.setGangId(2);
		when(killerGang.isAlly(victimGang)).thenReturn(true);
		now += 10 * 60_000L;
		dies(victimPlayer);
		assertEquals(0, killer.getKills());
	}

	@Test
	@DisplayName("ganglessVictim: a victim without a gang never counts, nor does a gangless killer")
	void ganglessVictim() {
		victim.resetGang();
		dies(victimPlayer);
		assertEquals(0, killer.getKills());

		victim.setGangId(2);
		killer.resetGang();
		now += 10 * 60_000L;
		dies(victimPlayer);
		assertEquals(0, victim.getDeaths());
	}

	@Test
	@DisplayName("youngVictim: a member younger than 24 hours is not a valid victim")
	void youngVictim() {
		victim.setGangJoinDateLong(NOW - 23 * HOUR);
		dies(victimPlayer);
		assertEquals(0, killer.getKills());

		victim.setGangJoinDateLong(NOW - 25 * HOUR);
		now += 10 * 60_000L;
		dies(victimPlayer);
		assertEquals(1, killer.getKills());
	}

	@Test
	@DisplayName("fourthKillSameVictimDay: the fourth kill of the same victim inside a day is not counted")
	void fourthKillSameVictimDay() {
		for (int i = 0; i < 4; i++) {
			dies(victimPlayer);
			now += 6 * 60_000L;
		}
		assertEquals(3, killer.getKills());
		assertEquals(3, victim.getDeaths());

		now += 25 * HOUR;
		dies(victimPlayer);
		assertEquals(4, killer.getKills());
	}

	@Test
	@DisplayName("repeatWithinCooldown: the same pair inside five minutes counts once")
	void repeatWithinCooldown() {
		dies(victimPlayer);
		now += 4 * 60_000L;
		dies(victimPlayer);
		assertEquals(1, killer.getKills());

		now += 2 * 60_000L;
		dies(victimPlayer);
		assertEquals(2, killer.getKills());
	}

	@Test
	@DisplayName("downedPlusDeath_dedup: a down and the death that follows count once, a stand-up forgets the down")
	void downedPlusDeath_dedup() {
		listener.onDowned(new PlayerDownedEvent(victimPlayer));
		now += 30_000L;
		dies(victimPlayer);
		assertEquals(1, killer.getKills());
		assertEquals(1, victim.getDeaths());

		// two events inside 500 ms count once as well
		now += 10 * 60_000L;
		listener.onDowned(new PlayerDownedEvent(victimPlayer));
		now += 100L;
		listener.onDowned(new PlayerDownedEvent(victimPlayer));
		assertEquals(2, killer.getKills());
	}

	@Test
	@DisplayName("respawnOn_oneShotCreditsShooter: the ledger saw nothing, so the vanilla killer is credited")
	void respawnOn_oneShotCreditsShooter() {
		ledger.last = null;
		when(victimPlayer.getKiller()).thenReturn(killerPlayer);

		dies(victimPlayer);

		assertEquals(1, killer.getKills());
		assertEquals(1, victim.getDeaths());
	}

	@Test
	@DisplayName("no killer at all (fall, lava) counts nothing")
	void noKiller() {
		ledger.last = null;
		dies(victimPlayer);

		assertEquals(0, victim.getDeaths());
	}

	@Test
	@DisplayName("assistWindow: ledger helpers get an assist, never the killer, the victim's gang or allies")
	void assistWindow() {
		Player helperPlayer = player();
		Member helper       = enroll(helperPlayer, 3, NOW - 100 * HOUR);
		gang(3);
		Player friendPlayer = player();
		Member friend       = enroll(friendPlayer, 2, NOW - 100 * HOUR);
		ledger.helpers.addAll(List.of(helperPlayer.getUniqueId(), friendPlayer.getUniqueId(),
		                              killerPlayer.getUniqueId()));

		dies(victimPlayer);

		assertEquals(1, helper.getAssists());
		assertEquals(0, friend.getAssists());
		assertEquals(0, killer.getAssists());
		assertEquals(1, killer.getKills());
	}

	@Test
	@DisplayName("disabledWorld: nothing counts in a world where gangs are off")
	void disabledWorld() {
		when(world.enabledIn(victimPlayer.getWorld())).thenReturn(false);

		dies(victimPlayer);

		assertEquals(0, killer.getKills());
		assertEquals(0, victim.getDeaths());
	}
}
