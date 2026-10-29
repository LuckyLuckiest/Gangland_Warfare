package org.luckyraven.gangland.copsncrooks.wanted.report;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CheckInSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.heat.ReportKind;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.21.2 S5 (PLAN.md section 2): a cop killed where nobody saw it goes silent; dispatch says Radio_Check at his last
 * check-in + Check_In_Seconds and Officer_Missing at + Silent_Seconds, then sends units to that spot and opens a scene.
 * The killer stands at (0, 64, 0); the dead cop last checked in at (5, 64, 5) two seconds before he died.
 */
@DisplayName("CopCrimeReports - silent officers, check-ins and scenes")
class CopCrimeReportsSilenceTest {

	private static final CopRole ASSAULT = new CopRole("Assault", "Assault", NpcFanPlacement.ANY, null, null, 1.0, null,
	                                                   0, null, 1.0, 0, null, 0, 60, false, false);

	private final World             world      = mock(World.class);
	private final Player            killer     = mock(Player.class);
	private final UUID              killerId   = UUID.randomUUID();
	private final Location          killerAt   = new Location(world, 0, 64, 0);
	private final Location          reportAt   = new Location(world, 5, 64, 5);
	private final ChaseConfigLoader config     = mock(ChaseConfigLoader.class);
	private final CopLoader         copLoader  = mock(CopLoader.class);
	private final CopConfigProvider provider   = mock(CopConfigProvider.class);
	private final CopManager        copManager = mock(CopManager.class);
	private final PatrolManager     patrol     = mock(PatrolManager.class);
	private final CopPerception     perception = new CopPerception();
	private final WitnessService    civilians  = mock(WitnessService.class);
	private final HeatLedger        ledger     = mock(HeatLedger.class);
	private final CrimeService      crimes     = mock(CrimeService.class);
	private final CopRadio          radio      = mock(CopRadio.class);
	private final WantedMessages    messages   = mock(WantedMessages.class);
	private final DetainmentService detainment = mock(DetainmentService.class);
	private final AtomicLong        clock      = new AtomicLong(1_000_000L);
	private final List<String>      notices    = new ArrayList<>();
	private final List<String>      debug      = new ArrayList<>();
	private final List<Event>       events     = new ArrayList<>();
	private final List<CopNpc>      squadCops  = new ArrayList<>();
	private final List<CopNpc>      beatCops   = new ArrayList<>();
	private final CopGroup          group      = new CopGroup(killerId);

	private CopCrimeReports reports;
	private CopNpc          victim;
	private LivingEntity    body;

	@BeforeEach
	void setUp() {
		when(killer.getUniqueId()).thenReturn(killerId);
		when(killer.getName()).thenReturn("Crook");
		when(killer.isOnline()).thenReturn(true);
		when(killer.getWorld()).thenReturn(world);
		when(killer.getLocation()).thenAnswer(inv -> killerAt.clone());

		when(config.getWitnesses()).thenReturn(WitnessSettings.DEFAULT);
		when(config.get()).thenReturn(ChaseConfig.DEFAULT);
		when(provider.getAlertRange()).thenReturn(40.0);
		when(provider.getCheckInSettings()).thenReturn(CheckInSettings.DEFAULT);
		when(provider.getBackupSettings()).thenReturn(BackupSettings.DEFAULT);
		when(provider.getHandoffSettings()).thenReturn(HandoffSettings.DEFAULT);
		when(copLoader.getLoadedProvider()).thenReturn(provider);
		when(copManager.liveCops()).thenAnswer(inv -> List.copyOf(squadCops));
		when(patrol.beatCops()).thenAnswer(inv -> List.copyOf(beatCops));
		when(copManager.groupOf(killerId)).thenReturn(group);
		when(ledger.commitReported(any(), any(), any())).thenAnswer(
				inv -> inv.<BooleanSupplier>getArgument(2).getAsBoolean());
		when(crimes.commit(any(), anyString(), any(), anyBoolean(), anyInt())).thenReturn(true);
		when(radio.now()).thenAnswer(inv -> clock.get());
		when(radio.memberName(any())).thenAnswer(inv -> ((CopNpc) inv.getArgument(0)).getCallsign());
		when(radio.placeOf(any())).thenReturn("the Docks");
		when(messages.crimeName(anyString())).thenAnswer(inv -> inv.<String>getArgument(0).replace('_', ' '));
		when(messages.format(any(WantedMessages.Key.class), anyMap())).thenAnswer(
				inv -> inv.<WantedMessages.Key>getArgument(0).name() + " " + new java.util.TreeMap<>(inv.<Map<String, String>>getArgument(1)));

		CopCrimeReports.Hooks hooks = new CopCrimeReports.Hooks(clock::get,
		                                                        (who, text) -> notices.add(who.getName() + "|" + text),
		                                                        task -> () -> { }, debug::add, events::add);
		reports = new CopCrimeReports(config, copLoader, copManager, patrol, perception, civilians, ledger, crimes, radio,
		                              messages, detainment, hooks);

		// the dead cop: invalid in his death event, a group cop who last checked in two seconds ago
		victim = mock(CopNpc.class);
		body   = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());
		when(body.getLocation()).thenAnswer(inv -> new Location(world, 8, 64, 8));
		when(victim.getEntity()).thenReturn(body);
		when(victim.isValid()).thenReturn(false);
		when(victim.getCallsign()).thenReturn("Vic");
		when(victim.getRole()).thenReturn(ASSAULT);
		when(victim.getGroup()).thenReturn(group);
		when(victim.getLastReport()).thenAnswer(inv -> reportAt.clone());
		when(victim.getLastReportAt()).thenReturn(clock.get() - 2_000L);
	}

	/** A living squad cop {@code x} blocks east of the killer looking along {@code yaw} (90 = west, toward him). */
	private CopNpc cop(String name, double x, float yaw, boolean seesKiller) {
		CopNpc       cop    = mock(CopNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		Location     at     = new Location(world, x, 64, 0, yaw, 0f);
		when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
		when(entity.getLocation()).thenAnswer(inv -> at.clone());
		when(cop.getEntity()).thenReturn(entity);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCallsign()).thenReturn(name);
		when(cop.getCurrentState()).thenReturn(CopState.PATROL);
		when(cop.canSee(eq(killer), anyDouble())).thenReturn(seesKiller);
		when(cop.getNpc()).thenReturn(mock(NPC.class));
		squadCops.add(cop);
		return cop;
	}

	private void advanceSeconds(double seconds) {
		clock.addAndGet((long) (seconds * 1000));
	}

	private boolean debugContains(String part) {
		return debug.stream().anyMatch(line -> line.contains(part));
	}

	private boolean silent() {
		return reports.silentPath(victim, body, killer);
	}

	private void verifyDispatch(String key, Location place, int times) {
		verify(radio, times(times)).dispatchAt(any(CopGroup.class), eq(place), eq(killer), eq(key), anyInt(), anyString(),
		                                       anyMap());
	}

	// ── who goes silent (PLAN.md 2.2) ───────────────────────────────────────

	@Test
	@DisplayName("0.21.2 T-580: a walker his group sent home holds no slot when he is killed unseen; his death is still silent")
	void unseenDeath_ofASentHomeWalker_holdsNoSlot() {
		when(victim.getCurrentState()).thenReturn(CopState.RETURNING);
		group.markSentHome(victim);

		assertTrue(silent());

		assertTrue(reports.silentRoles(group).isEmpty(), "he was no slot of the hunt");
		assertTrue(debugContains("CHECKIN silent Vic"), debug::toString);
	}

	@Test
	@DisplayName("0.21.2 T-580: a stranded walker past the leash holds no slot when he is killed unseen")
	void unseenDeath_ofAStrandedWalker_holdsNoSlot() {
		when(copManager.isStranded(victim, killer)).thenReturn(true);

		assertTrue(silent());

		assertTrue(reports.silentRoles(group).isEmpty(), "the spawn task had already stopped counting him");
	}

	@Test
	@DisplayName("0.21.2 T-580: a cop with no role still holds his slot (the whole silence counts, not its role)")
	void unseenDeath_roleless_stillHoldsASlot() {
		when(victim.getRole()).thenReturn(null);

		assertTrue(silent());

		assertEquals(1, reports.silentRoles(group).size());
	}

	@Test
	@DisplayName("nobody saw the killer or the body: the cop goes silent, his role still holds his group's slot")
	void unseenDeath_recordsSilence_holdsRole() {
		assertTrue(silent());

		assertEquals(List.of(ASSAULT), reports.silentRoles(group));
		assertTrue(reports.silentRoles(new CopGroup(UUID.randomUUID())).isEmpty());
		assertTrue(debugContains("CHECKIN silent Vic last=5,64,5 age=2"), debug::toString);
	}

	@Test
	@DisplayName("a cop who saw the killer opens a report: no silence, the radio is live (Man_Down)")
	void copSawTheKill_notSilent() {
		cop("Bob", 10, 90f, true);
		reports.open(killer, Crimes.KILL_COP, new Location(world, 8, 64, 8), body.getUniqueId());

		assertFalse(silent());
		assertTrue(reports.silentRoles(group).isEmpty());
	}

	@Test
	@DisplayName("no cop saw the killer but one saw the body fall: no silence, a scene opens at the body")
	void bodySeen_opensScene_notSilent() {
		CopNpc seer = cop("Ann", 30, 90f, false);
		when(seer.canSee(eq(body), anyDouble())).thenReturn(true);

		assertFalse(silent());

		assertEquals(1, reports.scenes(killerId));
		assertTrue(debugContains("SCENE open Crook at=8,64,8"), debug::toString);
		assertTrue(notices.stream().anyMatch(line -> line.contains("WITNESS_SCENE_HEADING")), notices::toString);
	}

	@Test
	@DisplayName("Check_In off, or Kill_Cop not held by the witness gate: today's path (the squad radios at once)")
	void checkInOff_orKillCopUngated_keepsTodaysPath() {
		when(provider.getCheckInSettings()).thenReturn(new CheckInSettings(false, 10, 20, 32.0, 60, 3, true, true));
		assertFalse(silent());

		when(provider.getCheckInSettings()).thenReturn(CheckInSettings.DEFAULT);
		when(config.getWitnesses()).thenReturn(new WitnessSettings(true, List.of("Kill_Civilian"), 10,
		                                                           WitnessSettings.Cops.DEFAULT));
		assertFalse(silent());
		assertTrue(reports.silentRoles(group).isEmpty());
	}

	// ── check-ins (PLAN.md 2.1) ─────────────────────────────────────────────

	@Test
	@DisplayName("a cop whose squad is in contact checks in every run; one on patrol every Check_In_Seconds")
	void checkIn_inContactStampsEveryRun_patrolEveryTenSeconds() {
		CopNpc   chasing = cop("Chaser", 10, 90f, false);
		NpcSquad contact = mock(NpcSquad.class);
		when(contact.isInContact()).thenReturn(true);
		when(chasing.getCurrentSquad()).thenReturn(contact);
		when(chasing.getLastReportAt()).thenAnswer(inv -> clock.get());

		CopNpc walking = cop("Walker", 20, 90f, false);
		long   stamped = clock.get();
		when(walking.getLastReportAt()).thenReturn(stamped);

		reports.tick();
		advanceSeconds(0.5);
		reports.tick();
		verify(chasing, times(2)).stampReport(any(Location.class), anyLong());
		verify(walking, never()).stampReport(any(Location.class), anyLong());

		advanceSeconds(9.5);
		reports.tick();
		verify(walking).stampReport(new Location(world, 20, 64, 0, 90f, 0f), stamped + 10_000L);
	}

	// ── the silence timeline (PLAN.md 2.4) ──────────────────────────────────

	@Test
	@DisplayName("Radio_Check at the last check-in + Check_In_Seconds, Officer_Missing at + Silent_Seconds, both about it")
	void silence_radioCheckThenMissing_atLastReport() {
		assertTrue(silent());

		advanceSeconds(7.9);
		reports.tick();
		verifyDispatch("Radio_Check", reportAt, 0);

		advanceSeconds(0.1);
		reports.tick();
		reports.tick();
		verifyDispatch("Radio_Check", reportAt, 1);
		assertTrue(debugContains("CHECKIN radio_check Vic last=5,64,5 age=10"), debug::toString);
		verifyDispatch("Officer_Missing", reportAt, 0);
		assertTrue(notices.stream().anyMatch(line -> line.contains("WITNESS_SILENT")), notices::toString);

		advanceSeconds(10);
		reports.tick();
		verifyDispatch("Officer_Missing", reportAt, 1);
		assertTrue(debugContains("CHECKIN missing Vic last=5,64,5 age=20"), debug::toString);
		assertTrue(reports.silentRoles(group).isEmpty());
	}

	@Test
	@DisplayName("Officer_Missing, group cop: casualty, backup (hold released), bias to the report, seed spent, exact tip")
	void missing_groupCop_biasToLastReport_seedSpent_tipsExactSpot_casualty_backup() {
		group.setBackupHeld(true);
		group.getSquad().reportTip(new Location(world, 40, 64, 40));
		assertTrue(silent());
		assertTrue(group.isBackupHeld(), "silent: the hold stays until dispatch notices");

		advanceSeconds(18);
		reports.tick();

		long now = clock.get();
		assertFalse(group.isBackupHeld());
		assertTrue(group.casualtyWithin(now, 0));
		assertTrue(group.backupExtra(now, BackupSettings.DEFAULT) > 0, "the backup request was granted");
		SpawnBias bias = group.biasAt(now);
		assertNotNull(bias);
		assertEquals(reportAt, bias.lastSeen());
		assertFalse(group.takeBiasSeed(bias), "the seed is spent: R5 never leads the units past the report");
		assertEquals(reportAt, group.getSquad().lastKnownLocation());
		assertTrue(group.tippedOffWithin(now, 0));
	}

	@Test
	@DisplayName("Officer_Missing fires the CopDeathEvent the silent death held back, not silent this time")
	void missing_firesDeferredCopDeathEvent() {
		assertTrue(silent());
		advanceSeconds(18);
		reports.tick();

		assertEquals(1, events.size());
		CopDeathEvent event = (CopDeathEvent) events.get(0);
		assertFalse(event.isSilent());
		assertEquals(victim, event.getNpc());
		assertEquals(killer, event.getKiller());
	}

	@Test
	@DisplayName("a beat cop: his station's held replacement is released and responds to the last check-in")
	void missing_beatCop_releasesHeldReplacement_twoUnitsInvestigate() {
		when(victim.getGroup()).thenReturn(null);
		when(patrol.stationOf(victim)).thenReturn(java.util.OptionalInt.of(7));
		assertTrue(silent());
		assertTrue(reports.silentStation(7));
		assertFalse(reports.silentStation(8));

		advanceSeconds(18);
		reports.tick();

		assertFalse(reports.silentStation(7));
		// 0.21.2 R2 (T-579): the responders hold the spot for the scene's Scene_Seconds (60)
		verify(patrol).respondTo(7, reportAt, 60_000L);
	}

	@Test
	@DisplayName("no group, no station: nobody is sent, the scene still opens at the last check-in")
	void missing_noGroupNoStation_sendsNobody_sceneOpens() {
		when(victim.getGroup()).thenReturn(null);
		when(copManager.groupOf(killerId)).thenReturn(null);
		assertTrue(silent());

		advanceSeconds(18);
		reports.tick();

		verify(patrol, never()).respondTo(anyInt(), any(), anyLong());
		assertEquals(1, reports.scenes(killerId));
		assertTrue(debugContains("SCENE open Crook at=5,64,5"), debug::toString);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("Pursuit_Presumed: his group was hunting the killer, so Kill_Cop commits at the report, x1.0, one witness")
	void pursuitPresumed_commitsWitnesses1_passesGate() {
		assertTrue(silent());
		advanceSeconds(18);
		reports.tick();

		InOrder order = inOrder(ledger, crimes);
		order.verify(ledger).commitReported(eq(killer), eq(ReportKind.DISPATCH), any());
		order.verify(crimes).commit(killer, Crimes.KILL_COP, reportAt, false, 1);
		assertEquals(0, reports.scenes(killerId), "presumed: no scene");
	}

	@Test
	@DisplayName("Pursuit_Presumed off: no commit at Officer_Missing, a scene opens instead")
	void pursuitPresumedOff_opensScene() {
		when(provider.getCheckInSettings()).thenReturn(new CheckInSettings(true, 10, 20, 32.0, 60, 3, false, true));
		assertTrue(silent());
		advanceSeconds(18);
		reports.tick();

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertEquals(1, reports.scenes(killerId));
	}

	// ── the scene ───────────────────────────────────────────────────────────

	private void openSceneAtReport() {
		when(victim.getGroup()).thenReturn(null);
		assertTrue(silent());
		advanceSeconds(18);
		reports.tick();
		assertEquals(1, reports.scenes(killerId));
	}

	@Test
	@DisplayName("a cop sees the killer within Scene_Radius: Kill_Cop commits as seen, a SCENE report, Scene_Suspect")
	void scene_killerSeenInRadius_commitsSeen() {
		openSceneAtReport();
		CopNpc bob = cop("Bob", 10, 90f, true);

		reports.tick();

		InOrder order = inOrder(ledger, crimes);
		order.verify(ledger).commitReported(eq(killer), eq(ReportKind.SCENE), any());
		order.verify(crimes).commit(killer, Crimes.KILL_COP, reportAt, true, 1);
		verify(radio).sayAs(any(CopGroup.class), eq(bob), eq("Scene_Suspect"), anyMap());
		assertTrue(debugContains("SCENE seen Crook at=5,64,5"), debug::toString);
		assertEquals(0, reports.scenes(killerId));
	}

	@Test
	@DisplayName("the killer seen outside Scene_Radius counts for nothing")
	void scene_killerSeenOutsideRadius_noCommit() {
		openSceneAtReport();
		when(provider.getCheckInSettings()).thenReturn(new CheckInSettings(true, 10, 20, 2.0, 60, 3, true, true));
		cop("Bob", 10, 90f, true);

		reports.tick();

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertEquals(1, reports.scenes(killerId));
	}

	@Test
	@DisplayName("the first unit within 6 blocks of the scene says Scene_Found, once")
	void scene_firstUnitThere_saysFoundOnce() {
		openSceneAtReport();
		CopNpc there = cop("Ann", 0, 270f, false);
		when(there.getEntity().getLocation()).thenAnswer(inv -> new Location(world, 7, 64, 5));

		reports.tick();
		reports.tick();

		verify(radio, times(1)).sayAs(any(CopGroup.class), eq(there), eq("Scene_Found"), anyMap());
		assertTrue(debugContains("SCENE found Crook at=5,64,5"), debug::toString);
	}

	/** Owner ruling T-601 (0.21.2 R3): a cop spawned within Spawn_Grace_Seconds (3) cannot close the scene. */
	@Test
	@DisplayName("0.21.2 R3 T-601: a unit spawned this tick does not close the scene; once his grace is over he does")
	void scene_unitSpawnedThisTick_doesNotCloseTheScene() {
		openSceneAtReport();
		CopNpc bob = cop("Bob", 10, 90f, true);
		when(bob.getSpawnedAt()).thenReturn(clock.get());

		reports.tick();
		advanceSeconds(2.9);
		reports.tick();

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertEquals(1, reports.scenes(killerId));
		assertTrue(debugContains("SCENE grace Crook at=5,64,5 cop=Bob"), debug::toString);

		advanceSeconds(0.1);
		reports.tick();

		verify(crimes).commit(killer, Crimes.KILL_COP, reportAt, true, 1);
		verify(radio).sayAs(any(CopGroup.class), eq(bob), eq("Scene_Suspect"), anyMap());
		assertEquals(0, reports.scenes(killerId));
	}

	@Test
	@DisplayName("0.21.2 R3 T-601: a unit spawned at the scene does not find it within his grace; Spawn_Grace_Seconds 0 is off")
	void scene_unitSpawnedAtTheScene_findsItOnlyAfterHisGrace() {
		openSceneAtReport();
		CopNpc there = cop("Ann", 0, 270f, false);
		when(there.getEntity().getLocation()).thenAnswer(inv -> new Location(world, 7, 64, 5));
		when(there.getSpawnedAt()).thenReturn(clock.get());

		reports.tick();
		verify(radio, never()).sayAs(any(CopGroup.class), eq(there), eq("Scene_Found"), anyMap());
		assertTrue(debugContains("SCENE grace Crook at=5,64,5 cop=Ann"), debug::toString);

		// R3 review 1: 0 is a hard off, even for a spawn stamp ahead of the scene clock (a backward clock step)
		when(provider.getCheckInSettings()).thenReturn(new CheckInSettings(true, 10, 20, 32.0, 60, 0, true, true));
		when(there.getSpawnedAt()).thenReturn(clock.get() + 1_000L);
		reports.tick();
		verify(radio, times(1)).sayAs(any(CopGroup.class), eq(there), eq("Scene_Found"), anyMap());
	}

	@Test
	@DisplayName("Scene_Seconds run out: Scene_Clear, the trail went cold, no stars")
	void scene_expires_noStars_sceneClear() {
		openSceneAtReport();

		advanceSeconds(59.9);
		reports.tick();
		assertEquals(1, reports.scenes(killerId));

		advanceSeconds(0.1);
		reports.tick();

		assertEquals(0, reports.scenes(killerId));
		verifyDispatch("Scene_Clear", reportAt, 1);
		assertTrue(notices.stream().anyMatch(line -> line.contains("WITNESS_SCENE_COLD")), notices::toString);
		assertTrue(debugContains("SCENE clear Crook at=5,64,5"), debug::toString);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	// ── the 911 call and the end of a silence ──────────────────────────────

	@Test
	@DisplayName("a 911 call for the kill lands first: it closes the silence, one commit, no Officer_Missing later")
	void nineOneOneFirst_closesSilence_noDoubleCommit() {
		assertTrue(silent());
		Location callAt = new Location(world, 8, 64, 8);

		advanceSeconds(8);
		reports.landCivilian(killer, Crimes.KILL_COP, callAt, 2);

		assertTrue(reports.silentRoles(group).isEmpty());
		assertEquals(1, events.size(), "the death is discovered by the call");
		assertEquals(callAt, group.biasAt(clock.get()).lastSeen(), "units biased to the call's spot");

		advanceSeconds(12);
		reports.tick();

		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(crimes).commit(killer, Crimes.KILL_COP, callAt, false, 2);
		verifyDispatch("Officer_Missing", reportAt, 0);
		assertEquals(0, reports.scenes(killerId));
	}

	@Test
	@DisplayName("the held role is gone after Officer_Missing, a logout, the killer's death; a torn-down group holds none")
	void silentRoles_emptyAfterMissing_logout_911_teardown() {
		assertTrue(silent());
		when(killer.isOnline()).thenReturn(false);
		reports.tick();
		assertTrue(reports.silentRoles(group).isEmpty(), "logout");

		when(killer.isOnline()).thenReturn(true);
		assertTrue(silent());
		reports.drop(killer, "death");
		assertTrue(reports.silentRoles(group).isEmpty(), "death");

		assertTrue(silent());
		CopGroup replaced = new CopGroup(killerId);
		when(copManager.groupOf(killerId)).thenReturn(replaced);
		assertTrue(reports.silentRoles(replaced).isEmpty(), "a new group never inherits the old one's held slot");
	}

	// ── one cop kill is one crime (S5 review 1) ─────────────────────────────

	@Test
	@DisplayName("a cop saw the assault, nobody saw the kill: the assault report stands, the radio stays live, one crime")
	void assaultReportStands_unseenKill_notSilent_oneCrime() {
		CopNpc bob = cop("Bob", 10, 90f, true);
		reports.open(killer, Crimes.ASSAULT_COP, new Location(world, 8, 64, 8), body.getUniqueId());
		when(bob.canSee(eq(killer), anyDouble())).thenReturn(false);
		advanceSeconds(1);
		reports.open(killer, Crimes.KILL_COP, new Location(world, 8, 64, 8), body.getUniqueId());
		assertTrue(debugContains("REPORT stand Crook crime=Assault_Cop"), debug::toString);

		assertFalse(silent(), "Bob is radioing the assault on him: the squad hears of the death");
		assertTrue(reports.silentRoles(group).isEmpty());

		advanceSeconds(3);
		reports.tick();
		advanceSeconds(20);
		reports.tick();

		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(crimes).commit(eq(killer), eq(Crimes.ASSAULT_COP), any(), eq(true), eq(1));
		verifyDispatch("Officer_Missing", reportAt, 0);
	}

	@Test
	@DisplayName("a body seen fall: the scene counts the kill when a cop spots him, then the 911 call commits nothing")
	void bodySeenScene_committed_then911_noSecondCommit() {
		CopNpc ann = cop("Ann", 30, 90f, false);
		when(ann.canSee(eq(body), anyDouble())).thenReturn(true);
		assertFalse(silent());
		when(ann.canSee(eq(killer), anyDouble())).thenReturn(true);
		reports.tick();
		verify(crimes).commit(killer, Crimes.KILL_COP, new Location(world, 8, 64, 8), true, 1);

		reports.landCivilian(killer, Crimes.KILL_COP, new Location(world, 8, 64, 8), 2);

		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verifyDispatch("Caller_911", new Location(world, 8, 64, 8), 0);
		assertTrue(debugContains("REPORT drop Crook crime=Kill_Cop reporter=CIVILIAN witnesses=2 at=8,64,8 reason=counted"),
		           debug::toString);
	}

	@Test
	@DisplayName("Officer_Missing presumed the kill: a 911 call about it landing later commits nothing")
	void missingPresumed_then911_noSecondCommit() {
		assertTrue(silent());
		advanceSeconds(18);
		reports.tick();
		verify(crimes).commit(killer, Crimes.KILL_COP, reportAt, false, 1);

		reports.landCivilian(killer, Crimes.KILL_COP, new Location(world, 8, 64, 8), 1);

		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("Officer_Missing presumed the kill: a 911 call about it cut short by his logout is not deferred")
	void missingPresumed_thenDeferred911_noSecondCommit() {
		assertTrue(silent());
		advanceSeconds(18);
		reports.tick();

		reports.defer(killer, Crimes.KILL_COP, new Location(world, 8, 64, 8), 2, ReportKind.CIVILIAN);
		reports.onJoin(killer);

		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("two cops killed unseen: a 911 call about the second closes his silence, the first stays silent")
	void twoSilences_911ClosesTheMatchingOne() {
		assertTrue(silent());
		CopNpc       second     = mock(CopNpc.class);
		LivingEntity secondBody = mock(LivingEntity.class);
		when(secondBody.getUniqueId()).thenReturn(UUID.randomUUID());
		when(secondBody.getLocation()).thenAnswer(inv -> new Location(world, 40, 64, 40));
		when(second.getEntity()).thenReturn(secondBody);
		when(second.getCallsign()).thenReturn("Sid");
		when(second.getRole()).thenReturn(ASSAULT);
		when(second.getGroup()).thenReturn(group);
		when(second.getLastReport()).thenAnswer(inv -> new Location(world, 38, 64, 38));
		when(second.getLastReportAt()).thenReturn(clock.get() - 2_000L);
		assertTrue(reports.silentPath(second, secondBody, killer));

		reports.landCivilian(killer, Crimes.KILL_COP, new Location(world, 40, 64, 40), 1);

		assertTrue(debugContains("CHECKIN close Sid"), debug::toString);
		assertFalse(debugContains("CHECKIN close Vic"), debug::toString);
		assertEquals(1, reports.silentRoles(group).size(), "Vic still holds his slot");
	}

	@Test
	@DisplayName("Pursuit_Presumed re-resolves his group at Officer_Missing: a group gone since opens a scene, no commit")
	void pursuitPresumed_groupGoneAtMissing_opensScene() {
		assertTrue(silent());
		when(copManager.groupOf(killerId)).thenReturn(null);
		advanceSeconds(18);
		reports.tick();

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertEquals(1, reports.scenes(killerId));
	}

	@Test
	@DisplayName("shutdown forgets every silence and scene")
	void shutdown_forgetsSilencesAndScenes() {
		assertTrue(silent());
		reports.onShutdown();

		assertTrue(reports.silentRoles(group).isEmpty());
		advanceSeconds(20);
		reports.tick();
		verifyDispatch("Officer_Missing", reportAt, 0);
	}
}
