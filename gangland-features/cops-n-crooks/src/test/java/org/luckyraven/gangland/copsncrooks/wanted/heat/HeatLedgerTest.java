package org.luckyraven.gangland.copsncrooks.wanted.heat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the heat ledger: weighted crimes add heat, streak/cop-sight/turf-war multipliers apply, and crossing star
 * thresholds replays the star trigger until the wanted level matches.
 */
@DisplayName("HeatLedger")
class HeatLedgerTest {

	private final UUID playerId = UUID.randomUUID();

	private Player            player;
	private Location          location;
	private Wanted            wanted;
	private CrimeService      crimes;
	private ChaseConfigLoader config;
	private AtomicLong        clock;
	private AtomicInteger     window;
	private AtomicBoolean     copSight;
	private AtomicBoolean     contested;
	private AtomicInteger     triggers;
	private HeatLedger        ledger;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		player   = mock(Player.class);
		location = mock(Location.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getLocation()).thenReturn(location);
		when(location.clone()).thenReturn(location);

		wanted = new Wanted(null, 1, 5);
		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		config = mock(ChaseConfigLoader.class);
		when(config.get()).thenReturn(ChaseConfig.DEFAULT);
		when(config.getWitnesses()).thenReturn(WitnessSettings.DEFAULT);

		crimes    = mock(CrimeService.class);
		clock     = new AtomicLong(1_000_000L);
		window    = new AtomicInteger(10);
		copSight  = new AtomicBoolean();
		contested = new AtomicBoolean();
		triggers  = new AtomicInteger();

		ledger = new HeatLedger(config, users, crimes, p -> copSight.get(), l -> contested.get(), window::get,
		                        clock::get);
		ledger.setStarTrigger(p -> {
			triggers.incrementAndGet();
			wanted.setLevel(wanted.getLevel() + 1);
		});
	}

	private CrimeCommittedEvent crime(String id) {
		return new CrimeCommittedEvent(player, id, location, false, 0);
	}

	private void heatOff() {
		HeatSettings d = HeatSettings.DEFAULT;
		HeatSettings off = new HeatSettings(false, d.starThresholds(), d.streakBonus(), d.seenByCopMultiplier(),
		                                    d.turfWarMultiplier(), d.assaultRepeatSeconds(), d.crimeWeights(),
		                                    d.perExtraWitness(), d.maxExtraWitnesses());
		when(config.get()).thenReturn(new ChaseConfig(off, ChaseConfig.DEFAULT.evasion(), ChaseConfig.DEFAULT.hud(),
		                                              ChaseConfig.DEFAULT.chargeSheet()));
	}

	@Test
	@DisplayName("one Assault_Cop gives the first star and is the last crime")
	void assaultCop_once_givesTheFirstStar_andIsTheLastCrime() {
		assertEquals(100, ledger.record(crime(Crimes.ASSAULT_COP)));

		assertEquals(1, wanted.getLevel());
		assertEquals(1, triggers.get());
		assertEquals(Crimes.ASSAULT_COP, ledger.lastCrime(playerId).crimeId());
	}

	@Test
	@DisplayName("one ignored warning (Brandish_Near_Cop seen by a cop) gives the first star under the shipped heat")
	void brandishNearCop_seenByCop_givesTheFirstStar() {
		ledger.record(new CrimeCommittedEvent(player, Crimes.BRANDISH_NEAR_COP, location, true, 0));

		assertEquals(1, wanted.getLevel());
		assertEquals(1, triggers.get());
		assertEquals(Crimes.BRANDISH_NEAR_COP, ledger.lastCrime(playerId).crimeId());
	}

	@Test
	@DisplayName("two Kill_Cop inside the streak window reach two stars (150 + 225)")
	void killCopTwiceInTheStreakWindow_reachesTwoStars() {
		assertEquals(150, ledger.record(crime(Crimes.KILL_COP)));
		clock.addAndGet(5_000);
		assertEquals(225, ledger.record(crime(Crimes.KILL_COP)));

		assertEquals(375, ledger.heatOf(playerId));
		assertEquals(2, wanted.getLevel());
	}

	@Test
	@DisplayName("a crime after the streak window gets no streak bonus")
	void crimeAfterTheWindow_hasNoStreakBonus() {
		ledger.record(crime(Crimes.KILL_COP));
		clock.addAndGet(11_000);

		assertEquals(150, ledger.record(crime(Crimes.KILL_COP)));
	}

	@Test
	@DisplayName("a crime the publisher saw a cop witness is multiplied by 1.5")
	void seenByCop_multipliesByOneAndAHalf() {
		CrimeCommittedEvent seen = new CrimeCommittedEvent(player, Crimes.KILL_COP, location, true, 1);

		assertEquals(225, ledger.record(seen));
	}

	@Test
	@DisplayName("the squad's own sight alone multiplies an ungated crime by 1.5; 0.21.2 S4: a shipped-gated Kill_Cop not")
	void copSight_aloneAlsoMultiplies() {
		copSight.set(true);

		assertEquals(150, ledger.record(crime(Crimes.RESISTING_ARREST)));
		// flipped 0.21.2 S4 (T-541): the bundled Witnesses.Crimes gate Kill_Cop, so only the cop who reports it counts
		window.set(0);
		assertEquals(150, ledger.record(crime(Crimes.KILL_COP)));
	}

	@Test
	@DisplayName("a contested turf halves Kill_Player only")
	void turfWar_halvesKillPlayerOnlyInsideAContestedTurf() {
		contested.set(true);
		assertEquals(40, ledger.record(crime(Crimes.KILL_PLAYER)));

		ledger.clear(playerId);
		assertEquals(150, ledger.record(crime(Crimes.KILL_COP)));

		ledger.clear(playerId);
		contested.set(false);
		assertEquals(80, ledger.record(crime(Crimes.KILL_PLAYER)));
	}

	@Test
	@DisplayName("a Jailbreak crosses three thresholds and triggers three times")
	void jailbreak_crossesThreeThresholds_triggersThreeTimes() {
		assertEquals(450, ledger.record(crime("Jailbreak")));

		assertEquals(3, triggers.get());
		assertEquals(3, wanted.getLevel());
	}

	@Test
	@DisplayName("an unknown crime and a disabled heat add nothing")
	void unknownCrime_andHeatDisabled_addNothing() {
		assertEquals(0, ledger.record(crime("Not_A_Crime")));
		assertNull(ledger.lastCrime(playerId));

		heatOff();
		assertEquals(0, ledger.record(crime(Crimes.KILL_COP)));
		assertEquals(0, triggers.get());
		assertTrue(ledger.chaseCrimes(playerId).isEmpty());
	}

	@Test
	@DisplayName("a level drop floors the heat at the new level's threshold")
	void levelDrop_floorsTheHeat() {
		ledger.record(crime("Jailbreak"));
		ledger.record(crime("Jailbreak"));

		ledger.onLevelChanged(playerId, 5, 2, 5, WantedCause.EVASION);

		assertEquals(250, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("an admin raise lifts the heat to the new level's floor")
	void adminRaise_liftsHeatToTheFloor() {
		ledger.record(crime(Crimes.ASSAULT_COP));

		ledger.onLevelChanged(playerId, 1, 4, 5, WantedCause.ADMIN);

		assertEquals(700, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("a crime-caused raise leaves the heat alone")
	void crimeRaise_leavesTheHeat() {
		ledger.record(crime(Crimes.ASSAULT_COP));

		ledger.onLevelChanged(playerId, 0, 4, 5, WantedCause.CRIME);

		assertEquals(100, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("a restored chase starts at the floor of its level")
	void restoredChase_startsAtTheFloorOfItsLevel() {
		ledger.onLevelChanged(playerId, 0, 3, 5, WantedCause.RESTORE);

		assertEquals(450, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("0.21.2 S3: the crime commitReported records carries its ReportKind and witness count; any other has none")
	void reportKind_storedOnRecord() {
		for (ReportKind kind : ReportKind.values()) {
			boolean committed = ledger.commitReported(player, kind, () -> {
				ledger.record(new CrimeCommittedEvent(player, Crimes.ASSAULT_COP, location, false, 3));
				return true;
			});
			assertTrue(committed);
			assertEquals(kind, ledger.lastCrime(playerId).kind());
			assertEquals(3, ledger.lastCrime(playerId).witnesses());
		}

		// a store hold-up or a safe alarm commits with one witness too: nobody reported it
		ledger.record(new CrimeCommittedEvent(player, Crimes.ASSAULT_COP, location, false, 1));
		assertNull(ledger.lastCrime(playerId).kind());
		assertEquals(1, ledger.lastCrime(playerId).witnesses());

		ledger.record(crime(Crimes.ASSAULT_COP));
		assertNull(ledger.lastCrime(playerId).kind());
	}

	@Test
	@DisplayName("0.21.2 S3: a report for another player never marks this player's crime")
	void reportKind_onlyForTheReportedCriminal() {
		Player other = mock(Player.class);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());

		ledger.commitReported(other, ReportKind.COP, () -> {
			ledger.record(crime(Crimes.ASSAULT_COP));
			return true;
		});

		assertNull(ledger.lastCrime(playerId).kind());
	}

	@Test
	@DisplayName("chase crimes keep their order and clear empties them")
	void chaseCrimes_keepOrder_andClearEmptiesThem() {
		ledger.record(crime(Crimes.ASSAULT_COP));
		ledger.record(crime(Crimes.KILL_COP));

		List<String> ids = ledger.chaseCrimes(playerId).stream().map(CrimeRecord::crimeId).toList();
		assertEquals(List.of(Crimes.ASSAULT_COP, Crimes.KILL_COP), ids);

		ledger.clear(playerId);

		assertTrue(ledger.chaseCrimes(playerId).isEmpty());
		assertEquals(0, ledger.heatOf(playerId));
	}

	@Test
	@DisplayName("the same victim inside the window commits once, then again after it")
	void reportAssault_sameVictimInsideTheWindow_commitsOnce_thenAgainAfterIt() {
		UUID victim = UUID.randomUUID();

		ledger.reportAssault(player, victim, location);
		clock.addAndGet(5_000);
		ledger.reportAssault(player, victim, location);
		verify(crimes, times(1)).commit(player, Crimes.ASSAULT_COP, location);

		ledger.reportAssault(player, UUID.randomUUID(), location);
		verify(crimes, times(2)).commit(player, Crimes.ASSAULT_COP, location);

		clock.addAndGet(6_000);
		ledger.reportAssault(player, victim, location);
		verify(crimes, times(3)).commit(player, Crimes.ASSAULT_COP, location);
	}

	@Test
	@DisplayName("T-544: a cop's death books Kill_Cop at the body while heat is on")
	void reportCopKill_heatOn_commitsKillCopAtTheBody() {
		Location body = mock(Location.class);

		ledger.reportCopKill(player, body, UUID.randomUUID());

		verify(crimes).commit(player, Crimes.KILL_COP, body);
	}

	@Test
	@DisplayName("T-544: with heat off the core kill combo counts the kill, so the death books nothing")
	void reportCopKill_heatOff_commitsNothing() {
		heatOff();

		ledger.reportCopKill(player, mock(Location.class), UUID.randomUUID());

		verifyNoInteractions(crimes);
	}

	@Test
	@DisplayName("0.21.2 S4: while an Assault_Cop or Kill_Cop commits, currentVictim names the hit cop (the gate's victim)")
	void currentVictim_namesTheHitCopWhileTheCommitRuns() {
		UUID                       hit  = UUID.randomUUID();
		UUID                       dead = UUID.randomUUID();
		java.util.List<UUID>       seen = new java.util.ArrayList<>();
		org.mockito.Mockito.doAnswer(inv -> {
			seen.add(ledger.currentVictim());
			return true;
		}).when(crimes).commit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
		                       org.mockito.ArgumentMatchers.any());

		ledger.reportAssault(player, hit, location);
		ledger.reportCopKill(player, location, dead);

		assertEquals(java.util.Arrays.asList(hit, dead), seen);
		assertNull(ledger.currentVictim(), "cleared after the commit");
	}

	@Test
	@DisplayName("0.21.2 S4 T-543: inside commitReported the landing crime's spot is the reported scene, for him only")
	void reportedScene_isTheLandingCrimeSpot_duringTheCommitOnly() {
		Location scene = mock(Location.class);
		when(scene.clone()).thenReturn(scene);
		Player   other = mock(Player.class);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());
		java.util.List<Location> mine   = new java.util.ArrayList<>();
		java.util.List<Location> theirs = new java.util.ArrayList<>();
		ledger.setStarTrigger(p -> {
			mine.add(ledger.reportedScene(playerId));
			theirs.add(ledger.reportedScene(other.getUniqueId()));
			wanted.setLevel(wanted.getLevel() + 1);
		});

		ledger.commitReported(player, ReportKind.COP, () -> {
			ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, scene, true, 1));
			return true;
		});

		assertEquals(List.of(scene), mine);
		assertEquals(java.util.Arrays.asList((Location) null), theirs);
		assertNull(ledger.reportedScene(playerId), "cleared after the landing");
		ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, scene, true, 1));
		assertTrue(mine.size() > 1, "the plain commit raised a star too");
		assertNull(mine.get(mine.size() - 1), "a plain commit is no late report: he is placed where he stands");
	}

	// ── 0.21.2 S3: who saw it (PLAN.md section 3) ───────────────────────────────

	/** Gates the three crimes of the section 3 table, as the plan's bundled Witnesses.Crimes will (S4). */
	private void gateTableCrimes() {
		when(config.getWitnesses()).thenReturn(new WitnessSettings(true, List.of(Crimes.KILL_COP, Crimes.KILL_CIVILIAN,
		                                                                         Crimes.ASSAULT_COP)));
	}

	@ParameterizedTest(name = "{0} seenByCop={1} witnesses={2} -> {3} heat, {4} star(s)")
	@DisplayName("T-540: the section 3 table - role (cop x1.5, else x1.0) plus 0.5 per eyewitness past the first, capped at 3")
	@CsvSource({
			// crime, seenByCop, witnesses, heat, stars     (column of PLAN.md section 3)
			"Kill_Cop,      false, 1, 150, 1",          // presumed / scene, 1 civ
			"Kill_Cop,      false, 3, 300, 2",          // 3 civ
			"Kill_Cop,      true,  1, 225, 1",          // 1 cop
			"Kill_Cop,      true,  2, 300, 2",          // 2 cops
			"Kill_Cop,      true,  3, 375, 2",          // 3 cops
			"Kill_Cop,      true,  4, 450, 3",          // 4+ cops
			"Kill_Cop,      true,  9, 450, 3",          // 4+ cops: Max_Extra_Witnesses caps the head count
			"Kill_Civilian, false, 1, 100, 1",          // 1 civ
			"Kill_Civilian, false, 3, 200, 1",          // 3 civ
			"Kill_Civilian, true,  1, 150, 1",          // 1 cop
			"Kill_Civilian, true,  2, 200, 1",          // 2 cops
			"Kill_Civilian, true,  3, 250, 2",          // 3 cops
			"Kill_Civilian, true,  4, 300, 2",          // 4+ cops
			"Assault_Cop,   false, 1, 100, 1",          // 1 civ
			"Assault_Cop,   false, 3, 200, 1",          // 3 civ
			"Assault_Cop,   true,  1, 150, 1",          // 1 cop
			"Assault_Cop,   true,  2, 200, 1",          // 2 cops
			"Assault_Cop,   true,  3, 250, 2",          // 3 cops
			"Assault_Cop,   true,  4, 300, 2",          // 4+ cops
	})
	void witnessTableRows(String crimeId, boolean seenByCop, int witnesses, double heat, int stars) {
		gateTableCrimes();
		// the squad has a fresh sighting: a gated crime must ignore it, so only the event's own flag counts as a cop
		copSight.set(true);

		assertEquals(heat, ledger.record(new CrimeCommittedEvent(player, crimeId, location, seenByCop, witnesses)));
		assertEquals(stars, wanted.getLevel());
	}

	@Test
	@DisplayName("T-540: two cop kills inside 10 s, each seen by one cop: 225 + 337.5 = 562.5, three stars")
	void twoSeenCopKillsInTheStreak_reachThreeStars() {
		ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, location, true, 1));
		clock.addAndGet(5_000);

		assertEquals(337.5, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, location, true, 1)));
		assertEquals(562.5, ledger.heatOf(playerId));
		assertEquals(3, wanted.getLevel());
	}

	@Test
	@DisplayName("T-540: zero or one witness scores exactly as before 0.21.2")
	void oneWitness_unchanged() {
		assertEquals(150, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, location, false, 0)));
		ledger.clear(playerId);
		assertEquals(150, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, location, false, 1)));
		ledger.clear(playerId);
		assertEquals(225, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, location, true, 1)));
	}

	@Test
	@DisplayName("T-538: a gated crime ignores the squad's 1.5 s sighting; an ungated one keeps it")
	void gatedCrime_ignoresSquadSight() {
		copSight.set(true);

		// Kill_Civilian is gated by the shipped Witnesses.Crimes: one civilian's call is x1.0, not a hidden x1.5
		assertEquals(100, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_CIVILIAN, location, false, 1)));
		ledger.clear(playerId);
		// Kill_Player is not gated: the squad watching him still counts as a cop who saw it
		assertEquals(120, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_PLAYER, location, false, 0)));
	}

	@Test
	@DisplayName("T-538 interim (S3 until S4): a held crime the gate lets through on squad sight alone scores x1.0")
	void heldCrime_squadSightOnly_scoresFlat() {
		// WitnessGateListener still passes a held crime at once while the squad has a fresh sighting; it arrives with
		// seenByCop false and no witness. 0.21.1 scored it x1.5 (100 -> 150); S4 replaces that bypass with a cop report.
		copSight.set(true);

		assertEquals(100, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_CIVILIAN, location, false, 0)));
		assertEquals(1, wanted.getLevel());
	}

	@Test
	@DisplayName("T-538: with Witnesses.Enable false nothing is gated, so the squad's sighting counts for every crime")
	void witnessesOff_squadSightCountsForListedCrimes() {
		when(config.getWitnesses()).thenReturn(new WitnessSettings(false, WitnessSettings.DEFAULT.crimes()));
		copSight.set(true);

		assertEquals(150, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_CIVILIAN, location, false, 0)));
	}

	@Test
	@DisplayName("T-540: Per_Extra_Witness 0 ignores the head count; Max_Extra_Witnesses 1 caps it at one extra")
	void witnessKnobs_zeroIgnores_capLimits() {
		HeatSettings d    = HeatSettings.DEFAULT;
		HeatSettings flat = new HeatSettings(true, d.starThresholds(), d.streakBonus(), d.seenByCopMultiplier(),
		                                     d.turfWarMultiplier(), d.assaultRepeatSeconds(), d.crimeWeights(), 0, 3);
		when(config.get()).thenReturn(new ChaseConfig(flat, ChaseConfig.DEFAULT.evasion(), ChaseConfig.DEFAULT.hud(),
		                                              ChaseConfig.DEFAULT.chargeSheet()));
		assertEquals(150, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, location, false, 4)));

		ledger.clear(playerId);
		HeatSettings capped = new HeatSettings(true, d.starThresholds(), d.streakBonus(), d.seenByCopMultiplier(),
		                                       d.turfWarMultiplier(), d.assaultRepeatSeconds(), d.crimeWeights(), 0.5, 1);
		when(config.get()).thenReturn(new ChaseConfig(capped, ChaseConfig.DEFAULT.evasion(), ChaseConfig.DEFAULT.hud(),
		                                              ChaseConfig.DEFAULT.chargeSheet()));
		assertEquals(225, ledger.record(new CrimeCommittedEvent(player, Crimes.KILL_COP, location, false, 4)));
	}
}
