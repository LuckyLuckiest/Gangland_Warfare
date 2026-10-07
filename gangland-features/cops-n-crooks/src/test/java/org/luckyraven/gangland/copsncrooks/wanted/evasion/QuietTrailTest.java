package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HideoutSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.QuietSpeedSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuietTrail}: the cold trail. Quiet time runs from the last heat-ledger crime on this chase, or from the chase
 * start; offline time is never quiet; a minute of quiet with no fresh sighting holds the next backup wave.
 */
@DisplayName("QuietTrail")
class QuietTrailTest {

	private static final long SEC = 1000L;
	private static final long MIN = 60_000L;
	private static final long T0  = 10_000_000L;

	private final long[] now = {T0};
	private final UUID   id  = UUID.randomUUID();

	private EvasionSettings   settings;
	private HeatLedger        ledger;
	private CrimeRecord       lastCrime;
	private ChaseArcs         arcs;
	private CopRadio          radio;
	private CopGroup          group;
	private NpcSquad          squad;
	private boolean           fresh;
	private Player            player;
	private QuietTrail        trail;

	@BeforeEach
	void setUp() {
		settings = EvasionSettings.DEFAULT;
		ChaseConfigLoader config = mock(ChaseConfigLoader.class);
		when(config.get()).thenAnswer(inv -> new ChaseConfig(null, settings, null, null));
		ledger = mock(HeatLedger.class);
		when(ledger.lastCrime(id)).thenAnswer(inv -> lastCrime);
		arcs  = new ChaseArcs(() -> now[0]);
		radio = mock(CopRadio.class);

		squad = mock(NpcSquad.class);
		when(squad.hasFreshSighting()).thenAnswer(inv -> fresh);
		group = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(squad);
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);

		trail = new QuietTrail(ledger, arcs, config, radio, () -> now[0]);
	}

	private void at(long offsetMs) {
		now[0] = T0 + offsetMs;
	}

	private void crimeAt(long at) {
		lastCrime = new CrimeRecord("test", 10, at, mock(Location.class));
	}

	@Test
	@DisplayName("no heat-ledger crime on the chase: quiet runs from the chase start, a sign-raised chase is cold from its first minute")
	void noLedgerCrime_quietFromChaseStart() {
		arcs.start(id, WantedCause.SIGN, 2);

		assertEquals(0, trail.quietMs(id, now[0]));
		at(90 * SEC);
		assertEquals(90 * SEC, trail.quietMs(id, now[0]));
		assertEquals(1.25, trail.speed(id, now[0]));
	}

	@Test
	@DisplayName("a new ledger crime resets quiet to that crime")
	void newCrime_resetsQuiet() {
		arcs.start(id, WantedCause.CRIME, 2);
		at(60 * SEC);
		crimeAt(now[0]);

		at(100 * SEC);

		assertEquals(40 * SEC, trail.quietMs(id, now[0]));
	}

	@Test
	@DisplayName("a stale sub-threshold ledger crime from before the chase never anchors it: quiet runs from the chase start")
	void staleLedgerCrimeBeforeTheChase_quietRunsFromChaseStart() {
		crimeAt(T0 - 2 * 60 * MIN);
		arcs.start(id, WantedCause.SIGN, 2);
		at(30 * SEC);

		assertEquals(30 * SEC, trail.quietMs(id, now[0]));
	}

	@Test
	@DisplayName("an arc pruned after a long absence is a RESTORE start with a new arc: the rejoin is not quiet")
	void longOfflinePrunedArc_rejoinIsNotQuiet() {
		crimeAt(T0);
		arcs.start(id, WantedCause.CRIME, 2);
		arcs.quit(id);
		at(40 * MIN);
		arcs.prune();
		arcs.start(id, WantedCause.RESTORE, 2);

		assertEquals(0, trail.quietMs(id, now[0]));
		at(40 * MIN + 5 * SEC);
		assertEquals(5 * SEC, trail.quietMs(id, now[0]));
	}

	@Test
	@DisplayName("no arc, no chase, not quiet")
	void noArc_isNotQuiet() {
		assertEquals(0, trail.quietMs(id, now[0]));
		assertEquals(1.0, trail.speed(id, now[0]));
	}

	@Test
	@DisplayName("offline time is never quiet: a crime, four minutes offline, the rejoin reads about nothing")
	void offlineTimeIsNotQuiet() {
		arcs.start(id, WantedCause.CRIME, 2);
		crimeAt(now[0]);
		at(1 * SEC);
		assertEquals(1 * SEC, trail.quietMs(id, now[0]));
		arcs.quit(id);

		at(1 * SEC + 4 * MIN);
		arcs.restore(id);
		at(2 * SEC + 4 * MIN);

		long quietMs = trail.quietMs(id, now[0]);
		assertEquals(2 * SEC, quietMs);
		assertEquals(1.0, trail.speed(id, now[0]));
		trail.tick(player, group);
		verify(group).setBackupHeld(false);
	}

	@Test
	@DisplayName("sixty quiet seconds with no fresh sighting hold backup, and the leader says Returning_To_Patrol once")
	void sixtySecondsQuiet_holdsBackup_andSaysReturningOnce() {
		arcs.start(id, WantedCause.SIGN, 2);

		at(59 * SEC);
		trail.tick(player, group);
		verify(group).setBackupHeld(false);
		verify(radio, never()).sayFromLeader(any(), eq("Returning_To_Patrol"));

		at(60 * SEC);
		trail.tick(player, group);
		at(61 * SEC);
		trail.tick(player, group);

		verify(group, atLeastOnce()).setBackupHeld(true);
		verify(radio, times(1)).sayFromLeader(group, "Returning_To_Patrol");
	}

	@Test
	@DisplayName("a fresh sighting never holds backup, however long he was quiet")
	void freshSighting_neverHolds() {
		arcs.start(id, WantedCause.SIGN, 2);
		fresh = true;
		at(120 * SEC);

		trail.tick(player, group);

		verify(group).setBackupHeld(false);
		verify(group, never()).setBackupHeld(true);
		verify(radio, never()).sayFromLeader(any(), any());
	}

	@Test
	@DisplayName("a new crime after the hold releases it and re-arms the line")
	void crimeAfterHold_releases_andRearmsTheLine() {
		arcs.start(id, WantedCause.SIGN, 2);
		at(60 * SEC);
		trail.tick(player, group);
		verify(radio, times(1)).sayFromLeader(group, "Returning_To_Patrol");

		at(61 * SEC);
		crimeAt(now[0]);
		at(62 * SEC);
		trail.tick(player, group);
		verify(group).setBackupHeld(false);

		at(61 * SEC + 60 * SEC);
		trail.tick(player, group);

		verify(radio, times(2)).sayFromLeader(group, "Returning_To_Patrol");
		assertTrue(trail.quietMs(id, now[0]) >= 60 * SEC);
	}

	@Test
	@DisplayName("Quiet_Speed.Enable false never holds backup and keeps speed 1.0")
	void disabled_neverHolds_speedOne() {
		settings = new EvasionSettings(true, 3, DropMode.ONE_STAR, settings.searchRadius(), settings.secondsToDrop(),
		                               2.0, AutoSettings.DEFAULT, HideoutSettings.DEFAULT,
		                               new QuietSpeedSettings(false, 0.25, 2.0, 60), 4.0);
		arcs.start(id, WantedCause.SIGN, 2);
		at(10 * MIN);

		trail.tick(player, group);

		assertEquals(1.0, trail.speed(id, now[0]));
		verify(group).setBackupHeld(false);
		verify(group, never()).setBackupHeld(true);
		verify(radio, never()).sayFromLeader(any(), any());
	}
}
