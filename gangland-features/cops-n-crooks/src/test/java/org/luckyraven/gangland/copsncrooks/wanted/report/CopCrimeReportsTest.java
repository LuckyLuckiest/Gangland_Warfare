package org.luckyraven.gangland.copsncrooks.wanted.report;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * 0.21.2 S4 (PLAN.md section 1): a gated crime a cop saw waits Report_Seconds for his radio call; a crime no cop saw goes
 * to the civilians' 911; nobody saw it, no star. The killer stands at (0, 64, 0); cops stand on the +X axis.
 */
@DisplayName("CopCrimeReports - a cop eyewitness radios a crime in")
class CopCrimeReportsTest {

	private final World             world      = mock(World.class);
	private final Player            killer     = mock(Player.class);
	private final UUID              killerId   = UUID.randomUUID();
	private final Location          killerAt   = new Location(world, 0, 64, 0);
	private final Location          crimeAt    = new Location(world, 1, 64, 1);
	private final ChaseConfigLoader config     = mock(ChaseConfigLoader.class);
	private final CopLoader         copLoader  = mock(CopLoader.class);
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
	private final List<Runnable>    timers     = new ArrayList<>();
	private final int[]             stopped    = {0};
	private final List<CopNpc>      squadCops  = new ArrayList<>();
	private final List<CopNpc>      beatCops   = new ArrayList<>();
	private final NpcSquad          squad      = new NpcSquad();
	private final CopGroup          group      = mock(CopGroup.class);
	private final Map<CopNpc, Runnable> deaths = new java.util.HashMap<>();

	private CopCrimeReports reports;

	@BeforeEach
	void setUp() {
		when(killer.getUniqueId()).thenReturn(killerId);
		when(killer.getName()).thenReturn("Crook");
		when(killer.isOnline()).thenReturn(true);
		when(killer.getWorld()).thenReturn(world);
		when(killer.getLocation()).thenAnswer(inv -> killerAt.clone());

		when(config.getWitnesses()).thenReturn(WitnessSettings.DEFAULT);
		when(config.get()).thenReturn(ChaseConfig.DEFAULT);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getAlertRange()).thenReturn(40.0);
		when(copLoader.getLoadedProvider()).thenReturn(provider);
		when(copManager.liveCops()).thenAnswer(inv -> List.copyOf(squadCops));
		when(patrol.beatCops()).thenAnswer(inv -> List.copyOf(beatCops));
		when(copManager.groupOf(killerId)).thenReturn(group);
		when(group.getSquad()).thenReturn(squad);
		when(ledger.commitReported(any(), any(), any())).thenAnswer(
				inv -> inv.<BooleanSupplier>getArgument(2).getAsBoolean());
		when(crimes.commit(any(), anyString(), any(), anyBoolean(), anyInt())).thenReturn(true);
		when(radio.memberName(any())).thenAnswer(inv -> ((CopNpc) inv.getArgument(0)).getCallsign());
		when(radio.placeOf(any())).thenReturn("the Docks");
		when(messages.crimeName(anyString())).thenAnswer(inv -> inv.<String>getArgument(0).replace('_', ' '));
		when(messages.format(any(WantedMessages.Key.class), anyMap())).thenAnswer(
				inv -> inv.<WantedMessages.Key>getArgument(0).name() + " " + new java.util.TreeMap<>(inv.<Map<String, String>>getArgument(1)));

		CopCrimeReports.Hooks hooks = new CopCrimeReports.Hooks(clock::get,
		                                                        (who, text) -> notices.add(who.getName() + "|" + text),
		                                                        task -> {
			                                                        timers.add(task);
			                                                        return () -> stopped[0]++;
		                                                        }, debug::add);
		reports = new CopCrimeReports(config, copLoader, copManager, patrol, perception, civilians, ledger, crimes, radio,
		                              messages, detainment, hooks);
	}

	/**
	 * A squad cop {@code x} blocks east of the killer with a clear line of sight, looking along {@code yaw}
	 * (90 = west, toward the killer; 270 = east, away from him).
	 */
	private CopNpc cop(String name, double x, float yaw) {
		CopNpc       cop    = mock(CopNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		UUID         id     = UUID.randomUUID();
		Location     at     = new Location(world, x, 64, 0, yaw, 0f);
		boolean[]    alive  = {true};
		when(entity.getUniqueId()).thenReturn(id);
		when(entity.getLocation()).thenAnswer(inv -> at.clone());
		when(cop.getEntity()).thenAnswer(inv -> alive[0] ? entity : null);
		when(cop.isValid()).thenAnswer(inv -> alive[0]);
		when(cop.getCallsign()).thenReturn(name);
		when(cop.getCurrentState()).thenReturn(CopState.PATROL);
		when(cop.canSee(eq(killer), anyDouble())).thenAnswer(
				inv -> alive[0] && Math.abs(x) <= inv.<Double>getArgument(1));
		when(cop.getNpc()).thenReturn(mock(NPC.class));
		// a dead cop is invalid: kill(cop) flips it
		deaths.put(cop, () -> alive[0] = false);
		squadCops.add(cop);
		return cop;
	}

	private void kill(CopNpc cop) {
		deaths.get(cop).run();
	}

	private void advanceSeconds(double seconds) {
		clock.addAndGet((long) (seconds * 1000));
	}

	private void open(String crimeId, UUID victim) {
		reports.open(killer, crimeId, crimeAt, victim);
	}

	private boolean debugContains(String part) {
		return debug.stream().anyMatch(line -> line.contains(part));
	}

	// ── who witnesses (PLAN.md 1.2) ─────────────────────────────────────────

	@Test
	@DisplayName("a cop kill one cop saw lands at Report_Seconds as seen by a cop, one witness, a COP report")
	void copKill_seenByOneCop_landsAtReportSeconds_seenByCop_count1() {
		cop("Bob", 10, 90f);

		open(Crimes.KILL_COP, UUID.randomUUID());
		advanceSeconds(2.9);
		reports.tick();
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());

		advanceSeconds(0.1);
		reports.tick();

		InOrder order = inOrder(ledger, crimes);
		order.verify(ledger).commitReported(eq(killer), eq(ReportKind.COP), any());
		order.verify(crimes).commit(killer, Crimes.KILL_COP, crimeAt, true, 1);
		assertEquals(0, reports.pending(killerId));
		assertTrue(debugContains("REPORT open Crook crime=Kill_Cop reporter=Bob witnesses=1 at=1,64,1"), debug::toString);
		assertTrue(debugContains("REPORT land Crook crime=Kill_Cop reporter=Bob witnesses=1 at=1,64,1"), debug::toString);
	}

	@Test
	@DisplayName("every eyewitness counts at landing: two cops in front of a cop kill commit two witnesses")
	void twoCopsSee_commitTwoWitnesses() {
		cop("Bob", 10, 90f);
		cop("Ann", 20, 90f);

		open(Crimes.KILL_COP, UUID.randomUUID());
		advanceSeconds(3);
		reports.tick();

		verify(crimes).commit(killer, Crimes.KILL_COP, crimeAt, true, 2);
	}

	@Test
	@DisplayName("the dead cop of a Kill_Cop never witnesses his own death, even in his dying tick")
	void victimNeverWitnesses() {
		CopNpc victim = cop("Vic", 2, 90f);
		UUID   id     = victim.getEntity().getUniqueId();

		open(Crimes.KILL_COP, id);

		assertEquals(0, reports.pending(killerId));
		verify(civilians).witness(killer, Crimes.KILL_COP, crimeAt);
	}

	@Test
	@DisplayName("the hit cop is engaged with his attacker (COMBAT): he witnesses his own assault, facing away or not")
	void hitCop_engaged_witnessesOwnAssault() {
		CopNpc hit = cop("Hit", 3, 270f);
		when(hit.getTargetPlayerId()).thenReturn(killerId);
		when(hit.getCurrentState()).thenReturn(CopState.COMBAT);

		open(Crimes.ASSAULT_COP, hit.getEntity().getUniqueId());
		advanceSeconds(3);
		reports.tick();

		verify(crimes).commit(killer, Crimes.ASSAULT_COP, crimeAt, true, 1);
	}

	@Test
	@DisplayName("a beat cop facing an unwanted player sees the crime and reports it")
	void beatCopSeesUnwantedPlayer_reports() {
		CopNpc beat = cop("Beat", 8, 90f);
		squadCops.remove(beat);
		beatCops.add(beat);
		when(copManager.groupOf(killerId)).thenReturn(null);

		open(Crimes.KILL_CIVILIAN, null);
		advanceSeconds(3);
		reports.tick();

		verify(crimes).commit(killer, Crimes.KILL_CIVILIAN, crimeAt, true, 1);
		verify(civilians, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("a cop with his back turned, not engaged, a silent melee kill: no cop witness, the civilians are asked")
	void copBehind_outsideCone_meleeKill_noWitness() {
		cop("Bob", 10, 270f);

		open(Crimes.KILL_CIVILIAN, null);

		assertEquals(0, reports.pending(killerId));
		verify(civilians).witness(killer, Crimes.KILL_CIVILIAN, crimeAt);
	}

	@Test
	@DisplayName("a gunshot is loud (the civilians' shot clock): the cop behind turns to the noise and witnesses")
	void gunshot_turnsCop() {
		CopNpc bob = cop("Bob", 10, 270f);
		when(civilians.loudWithin(killer)).thenReturn(true);

		open(Crimes.KILL_CIVILIAN, null);

		assertEquals(1, reports.pending(killerId));
		verify(bob.getNpc()).faceLocation(crimeAt);
		verify(civilians, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("a Loud_Crimes crime turns the cop behind too")
	void loudCrime_turnsCop() {
		cop("Bob", 10, 270f);
		when(config.getWitnesses()).thenReturn(new WitnessSettings(true, List.of(Crimes.CAR_THEFT), 10,
		                                                           new WitnessSettings.Cops(3, 180.0, 6.0,
		                                                                                    List.of(Crimes.CAR_THEFT),
		                                                                                    true)));

		open(Crimes.CAR_THEFT, null);

		assertEquals(1, reports.pending(killerId));
	}

	@Test
	@DisplayName("View_Angle 360: a cop sees all round")
	void viewAngle360_seesAllRound() {
		cop("Bob", 10, 270f);
		when(config.getWitnesses()).thenReturn(new WitnessSettings(true, WitnessSettings.DEFAULT.crimes(), 10,
		                                                           new WitnessSettings.Cops(3, 360.0, 6.0, List.of(),
		                                                                                    true)));

		open(Crimes.KILL_CIVILIAN, null);

		assertEquals(1, reports.pending(killerId));
	}

	@Test
	@DisplayName("darkness and sneaking shrink a cop's sight to nothing, yet a cop within Witness_Floor still sees it")
	void nightSneak_withinFloor_stillWitnessed() {
		perception.addSightFactor((cop, target) -> 0.0);
		cop("Near", 5, 90f);
		cop("Far", 7, 90f);

		open(Crimes.KILL_CIVILIAN, null);
		advanceSeconds(3);
		reports.tick();

		verify(crimes).commit(killer, Crimes.KILL_CIVILIAN, crimeAt, true, 1);
	}

	@Test
	@DisplayName("a disguise stops a lock-on, not an eyewitness: a murder in front of a cop is reported")
	void disguisedKiller_stillWitnessed() {
		perception.addLockOnFilter((cop, target) -> false);
		cop("Bob", 10, 90f);

		open(Crimes.KILL_CIVILIAN, null);

		assertEquals(1, reports.pending(killerId));
	}

	@Test
	@DisplayName("a cop out of Alert_Range does not witness")
	void copOutOfRange_noWitness() {
		cop("Bob", 41, 90f);

		open(Crimes.KILL_CIVILIAN, null);

		assertEquals(0, reports.pending(killerId));
	}

	// ── the report (PLAN.md 1.4) ────────────────────────────────────────────

	@Test
	@DisplayName("the nearest witness reports and glows; the killer sees him radio it in with the seconds left")
	void nearestWitnessReports_glows_countdownShown() {
		CopNpc far  = cop("Far", 20, 90f);
		CopNpc near = cop("Near", 10, 90f);

		open(Crimes.KILL_COP, UUID.randomUUID());

		verify(near.getEntity()).setGlowing(true);
		verify(far.getEntity(), never()).setGlowing(true);
		assertEquals("Crook|WITNESS_COP_CALLING {reporter=Near, seconds=3}", notices.get(0));

		advanceSeconds(1.5);
		reports.tick();
		assertEquals("Crook|WITNESS_COP_CALLING {reporter=Near, seconds=2}", notices.get(notices.size() - 1));

		advanceSeconds(1.5);
		reports.tick();
		verify(near.getEntity()).setGlowing(false);
	}

	@Test
	@DisplayName("Reporter_Glow false: nobody glows")
	void reporterGlowOff() {
		CopNpc bob = cop("Bob", 10, 90f);
		when(config.getWitnesses()).thenReturn(new WitnessSettings(true, WitnessSettings.DEFAULT.crimes(), 10,
		                                                           new WitnessSettings.Cops(3, 180.0, 6.0, List.of(),
		                                                                                    false)));

		open(Crimes.KILL_COP, UUID.randomUUID());

		verify(bob.getEntity(), never()).setGlowing(true);
	}

	@Test
	@DisplayName("the reporter is killed: the next living witness takes the call over and keeps the same deadline")
	void reporterKilled_nextWitnessKeepsDeadline() {
		CopNpc near = cop("Near", 10, 90f);
		CopNpc far  = cop("Far", 20, 90f);

		open(Crimes.KILL_COP, UUID.randomUUID());
		advanceSeconds(1);
		kill(near);
		reports.tick();
		verify(far.getEntity()).setGlowing(true);

		advanceSeconds(2);
		reports.tick();

		verify(crimes).commit(killer, Crimes.KILL_COP, crimeAt, true, 1);
		assertTrue(debugContains("REPORT land Crook crime=Kill_Cop reporter=Far witnesses=1"), debug::toString);
	}

	@Test
	@DisplayName("every witness killed before the report: the call never went out, nothing is committed")
	void allWitnessesKilled_reportCut() {
		CopNpc bob = cop("Bob", 10, 90f);

		open(Crimes.KILL_COP, UUID.randomUUID());
		advanceSeconds(1);
		kill(bob);
		reports.tick();
		advanceSeconds(5);
		reports.tick();

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertEquals(0, reports.pending(killerId));
		assertEquals("Crook|WITNESS_REPORT_CUT {}", notices.get(notices.size() - 1));
		assertTrue(debugContains("REPORT cut Crook crime=Kill_Cop reporter=Bob witnesses=0"), debug::toString);
	}

	@Test
	@DisplayName("a Kill_Cop of a cop whose assault is pending supersedes the assault and keeps its deadline")
	void killSupersedesPendingAssault_keepsDeadline() {
		CopNpc hit = cop("Hit", 3, 90f);
		cop("Bob", 10, 90f);
		UUID hitId = hit.getEntity().getUniqueId();

		open(Crimes.ASSAULT_COP, hitId);
		advanceSeconds(2);
		kill(hit);
		open(Crimes.KILL_COP, hitId);
		assertEquals(1, reports.pending(killerId));

		advanceSeconds(1);
		reports.tick();

		verify(crimes).commit(killer, Crimes.KILL_COP, crimeAt, true, 1);
		verify(crimes, never()).commit(eq(killer), eq(Crimes.ASSAULT_COP), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("no cop and no civilian saw it: Nobody saw that, no report")
	void nobodySaw_noWitnessLine() {
		open(Crimes.KILL_CIVILIAN, null);

		assertEquals(0, reports.pending(killerId));
		assertEquals(List.of("Crook|WITNESS_NO_WITNESS {}"), notices);
		assertTrue(debugContains("WITNESS Crook crime=Kill_Civilian callers=0"), debug::toString);
	}

	@Test
	@DisplayName("a civilian called it in: no Nobody saw that line")
	void civilianCalls_noNoWitnessLine() {
		when(civilians.witness(any(), any(), any())).thenReturn(1);

		open(Crimes.KILL_CIVILIAN, null);

		assertTrue(notices.isEmpty(), notices::toString);
	}

	@Test
	@DisplayName("a cop saw it: the civilians are never asked, so there is one reporter per crime")
	void copEyes_suppressCivilianCall() {
		cop("Bob", 10, 90f);

		open(Crimes.KILL_CIVILIAN, null);

		verify(civilians, never()).witness(any(), any(), any());
	}

	@Test
	@DisplayName("R4: the landing tips the squad to the crime spot, not to where the killer stands by then")
	void landing_tipsAtCrimeSpot_notLivePosition() {
		cop("Bob", 10, 90f);

		open(Crimes.KILL_COP, UUID.randomUUID());
		killerAt.setX(30);
		advanceSeconds(3);
		reports.tick();

		assertEquals(crimeAt, squad.lastKnownLocation());
		assertFalse(squad.hasFreshSighting());
	}

	@Test
	@DisplayName("T-543: dispatch says Officer_Report about the crime spot, the reporter and the crime by name")
	void landing_radioPlaceIsCrimeSpot() {
		CopNpc bob = cop("Bob", 10, 90f);

		open(Crimes.KILL_COP, UUID.randomUUID());
		advanceSeconds(3);
		reports.tick();

		verify(radio).dispatchAt(group, crimeAt, killer, "Officer_Report", 0, "",
		                         Map.of("reporter", "Bob", "crime", "Kill Cop"));
	}

	@Test
	@DisplayName("a landing that created no group (heat under the first star) says nothing on the radio")
	void landing_noGroup_noRadio() {
		cop("Bob", 10, 90f);
		when(copManager.groupOf(killerId)).thenReturn(null);

		open(Crimes.CAR_THEFT, null);
		advanceSeconds(3);
		reports.tick();

		verify(crimes).commit(killer, Crimes.CAR_THEFT, crimeAt, true, 1);
		verify(radio, never()).dispatchAt(any(), any(), any(), anyString(), anyInt(), anyString(), anyMap());
	}

	// ── the killer's state (PLAN.md 1.4) ────────────────────────────────────

	@Test
	@DisplayName("the killer dies: his reports are dropped (death resets wanted)")
	void killerDeath_dropsCases() {
		CopNpc bob = cop("Bob", 10, 90f);
		open(Crimes.KILL_COP, UUID.randomUUID());

		reports.drop(killer, "death");
		advanceSeconds(3);
		reports.tick();

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(bob.getEntity()).setGlowing(false);
		assertTrue(debugContains("REPORT drop Crook crime=Kill_Cop reporter=Bob witnesses=1 at=1,64,1 reason=death"),
		           debug::toString);
	}

	@Test
	@DisplayName("the killer is cuffed: the arrest settles him, no star lands after the cuff")
	void killerCuffed_dropsCases() {
		cop("Bob", 10, 90f);
		open(Crimes.ASSAULT_COP, UUID.randomUUID());

		reports.drop(killer, "cuffed");
		advanceSeconds(3);
		reports.tick();

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("restrained when the report would land: dropped")
	void restrainedAtLanding_drops() {
		cop("Bob", 10, 90f);
		open(Crimes.KILL_COP, UUID.randomUUID());
		when(detainment.isRestrained(killer)).thenReturn(true);

		advanceSeconds(3);
		reports.tick();

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertEquals(0, reports.pending(killerId));
		assertTrue(debugContains("reason=restrained"), debug::toString);
	}

	@Test
	@DisplayName("review r1: restrained at the join (cuffed during the call, jailed by the transit intake): the deferred report is dropped")
	void deferredReport_restrainedAtJoin_drops() {
		cop("Bob", 10, 90f);
		open(Crimes.KILL_COP, UUID.randomUUID());
		reports.onQuit(killer);
		reports.defer(killer, Crimes.CAR_THEFT, crimeAt, 2, ReportKind.CIVILIAN);
		when(detainment.isRestrained(killer)).thenReturn(true);

		reports.onJoin(killer);

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertTrue(debugContains("REPORT drop Crook crime=Kill_Cop reporter=Bob witnesses=1 at=1,64,1 reason=restrained"),
		           debug::toString);
		assertFalse(notices.contains("Crook|WITNESS_DEFERRED {}"), notices::toString);
	}

	@Test
	@DisplayName("review r1: a drop (death, cuff, arrest) also forgets the reports deferred to his next join")
	void drop_alsoForgetsDeferred() {
		reports.defer(killer, Crimes.CAR_THEFT, crimeAt, 1, ReportKind.CIVILIAN);

		reports.drop(killer, "arrested");
		reports.onJoin(killer);

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("review r1: a cop reporting two crimes keeps glowing until his last report is closed")
	void reporterOfTwoCases_keepsGlowingUntilTheLast() {
		CopNpc bob = cop("Bob", 10, 90f);
		open(Crimes.KILL_CIVILIAN, null);
		advanceSeconds(1);
		open(Crimes.CAR_THEFT, null);

		advanceSeconds(2);
		reports.tick();
		verify(crimes).commit(killer, Crimes.KILL_CIVILIAN, crimeAt, true, 1);
		verify(bob.getEntity(), never()).setGlowing(false);

		advanceSeconds(1);
		reports.tick();
		verify(crimes).commit(killer, Crimes.CAR_THEFT, crimeAt, true, 1);
		verify(bob.getEntity()).setGlowing(false);
	}

	@Test
	@DisplayName("review r1: a quit after the reporter died defers the report under the next living witness")
	void quit_reporterDead_defersUnderNextWitness() {
		CopNpc near = cop("Near", 10, 90f);
		cop("Far", 20, 90f);
		open(Crimes.KILL_COP, UUID.randomUUID());
		kill(near);

		reports.onQuit(killer);
		reports.onJoin(killer);

		assertTrue(debugContains("REPORT defer Crook crime=Kill_Cop reporter=Far witnesses=1"), debug::toString);
		verify(radio).dispatchAt(group, crimeAt, killer, "Officer_Report", 0, "",
		                         Map.of("reporter", "Far", "crime", "Kill Cop"));
	}

	@Test
	@DisplayName("review r1: a cop kill only the victim's assault case saw (no cop, no civilian): the case closes silently")
	void killUnseen_assaultOnlyTheVictimSaw_closesWithoutReportCut() {
		CopNpc hit = cop("Hit", 3, 270f);
		when(hit.getTargetPlayerId()).thenReturn(killerId);
		when(hit.getCurrentState()).thenReturn(CopState.COMBAT);
		UUID hitId = hit.getEntity().getUniqueId();
		open(Crimes.ASSAULT_COP, hitId);
		notices.clear();

		kill(hit);
		open(Crimes.KILL_COP, hitId);
		advanceSeconds(3);
		reports.tick();

		assertEquals(0, reports.pending(killerId));
		assertEquals(List.of("Crook|WITNESS_NO_WITNESS {}"), notices);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("review r2: a cop kill no cop saw while another living cop saw the assault: the civilians are never "
	             + "asked, the assault lands (a cop report beats a call, one cop is one crime)")
	void killUnseenByCops_anotherCopSawTheAssault_civiliansNotAsked_assaultLands() {
		CopNpc hit = cop("Hit", 3, 90f);
		cop("Bob", 10, 90f);
		UUID hitId = hit.getEntity().getUniqueId();
		open(Crimes.ASSAULT_COP, hitId);
		kill(hit);
		// Bob looks away by the kill: only civilians see it
		perception.addSightFactor((cop, target) -> 0.0);
		when(civilians.witness(killer, Crimes.KILL_COP, crimeAt)).thenReturn(1);

		open(Crimes.KILL_COP, hitId);
		// past the 911 call: the would-be caller may be dead by now, a living cop still saw the assault
		advanceSeconds(10);
		reports.tick();

		verify(civilians, never()).witness(any(), eq(Crimes.KILL_COP), any());
		verify(crimes).commit(killer, Crimes.ASSAULT_COP, crimeAt, true, 1);
		assertFalse(debugContains("reason=superseded"), debug::toString);
		assertTrue(debug.stream().anyMatch(line -> line.startsWith("REPORT stand Crook crime=Assault_Cop ")
		                                                   && line.endsWith(" reason=unseen_kill")), debug::toString);
	}

	@Test
	@DisplayName("review r2: a cop kill no cop saw, the assault only the victim saw, a civilian calls the kill in: the "
	             + "assault case closes, one crime")
	void killUnseenByCops_victimOnlySawAssault_civilianCalls_assaultClosed() {
		CopNpc hit = cop("Hit", 3, 270f);
		when(hit.getTargetPlayerId()).thenReturn(killerId);
		when(hit.getCurrentState()).thenReturn(CopState.COMBAT);
		UUID hitId = hit.getEntity().getUniqueId();
		open(Crimes.ASSAULT_COP, hitId);
		kill(hit);
		when(civilians.witness(killer, Crimes.KILL_COP, crimeAt)).thenReturn(1);

		open(Crimes.KILL_COP, hitId);
		advanceSeconds(3);
		reports.tick();

		assertEquals(0, reports.pending(killerId));
		verify(crimes, never()).commit(eq(killer), eq(Crimes.ASSAULT_COP), any(), anyBoolean(), anyInt());
		assertTrue(debugContains("REPORT drop Crook crime=Assault_Cop reporter=Hit witnesses=1 at=1,64,1 reason=superseded"),
		           debug::toString);
	}

	@Test
	@DisplayName("review r2: a report whose commit is refused logs drop reason=refused, not land")
	void landing_commitRefused_logsRefused() {
		cop("Bob", 10, 90f);
		when(crimes.commit(any(), anyString(), any(), anyBoolean(), anyInt())).thenReturn(false);
		open(Crimes.KILL_COP, UUID.randomUUID());

		advanceSeconds(3);
		reports.tick();

		assertTrue(debugContains("REPORT drop Crook crime=Kill_Cop reporter=Bob witnesses=1 at=1,64,1 reason=refused"),
		           debug::toString);
		assertFalse(debugContains("REPORT land"), debug::toString);
	}

	@Test
	@DisplayName("review r2: a deferred report whose commit is refused at the join: no Witness.Deferred line")
	void deferredReport_commitRefused_noDeferredLine() {
		when(crimes.commit(any(), anyString(), any(), anyBoolean(), anyInt())).thenReturn(false);
		reports.defer(killer, Crimes.CAR_THEFT, crimeAt, 2, ReportKind.CIVILIAN);

		reports.onJoin(killer);

		assertFalse(notices.contains("Crook|WITNESS_DEFERRED {}"), notices::toString);
		assertTrue(debugContains("REPORT drop Crook crime=Car_Theft reporter=CIVILIAN witnesses=2 at=1,64,1 reason=refused"),
		           debug::toString);
	}

	@Test
	@DisplayName("review r1: a cop kill nobody saw while another cop saw the assault: the assault still lands, one crime")
	void killUnseen_anotherCopSawTheAssault_assaultLands() {
		CopNpc hit = cop("Hit", 3, 90f);
		cop("Bob", 10, 90f);
		UUID hitId = hit.getEntity().getUniqueId();
		open(Crimes.ASSAULT_COP, hitId);
		kill(hit);
		perception.addSightFactor((cop, target) -> 0.0);

		open(Crimes.KILL_COP, hitId);
		advanceSeconds(3);
		reports.tick();

		verify(crimes).commit(killer, Crimes.ASSAULT_COP, crimeAt, true, 1);
		verify(crimes, never()).commit(eq(killer), eq(Crimes.KILL_COP), any(), anyBoolean(), anyInt());
		assertFalse(notices.contains("Crook|WITNESS_NO_WITNESS {}"), notices::toString);
	}

	@Test
	@DisplayName("review r1: a squad in live contact keeps its sighting; the landing still names the crime spot")
	void landing_squadInContact_keepsTheSighting() {
		cop("Bob", 10, 90f);
		open(Crimes.KILL_COP, UUID.randomUUID());
		Location live = new Location(world, 30, 64, 0);
		squad.reportSighting(live);

		advanceSeconds(3);
		reports.tick();

		assertEquals(live, squad.lastKnownLocation());
		verify(radio).dispatchAt(group, crimeAt, killer, "Officer_Report", 0, "",
		                         Map.of("reporter", "Bob", "crime", "Kill Cop"));
	}

	@Test
	@DisplayName("OD-5: a quit with a witness alive defers the report; it lands one join later, within Defer_Minutes")
	void quit_defersPending_commitsOnJoin_expiresAfterDeferMinutes() {
		cop("Bob", 10, 90f);
		open(Crimes.KILL_COP, UUID.randomUUID());

		reports.onQuit(killer);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertEquals(0, reports.pending(killerId));
		assertTrue(debugContains("REPORT defer Crook crime=Kill_Cop reporter=Bob witnesses=1"), debug::toString);

		advanceSeconds(9 * 60);
		reports.onJoin(killer);

		verify(ledger).commitReported(eq(killer), eq(ReportKind.COP), any());
		verify(crimes).commit(killer, Crimes.KILL_COP, crimeAt, true, 1);
		assertEquals("Crook|WITNESS_DEFERRED {}", notices.get(notices.size() - 1));

		// a second report deferred and not back within Defer_Minutes is forgotten
		open(Crimes.KILL_COP, UUID.randomUUID());
		reports.onQuit(killer);
		advanceSeconds(10 * 60 + 1);
		reports.onJoin(killer);
		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("a quit after every witness died defers nothing")
	void quit_noLiveWitness_defersNothing() {
		CopNpc bob = cop("Bob", 10, 90f);
		open(Crimes.KILL_COP, UUID.randomUUID());
		kill(bob);

		reports.onQuit(killer);
		reports.onJoin(killer);

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("T-551: a deferred 911 call lands at the next join as a CIVILIAN report with its viewers")
	void deferredCivilianCall_landsOnJoin() {
		reports.defer(killer, Crimes.CAR_THEFT, crimeAt, 3, ReportKind.CIVILIAN);

		reports.onJoin(killer);

		verify(ledger).commitReported(eq(killer), eq(ReportKind.CIVILIAN), any());
		verify(crimes).commit(killer, Crimes.CAR_THEFT, crimeAt, false, 3);
		assertEquals(crimeAt, squad.lastKnownLocation());
		verify(radio).dispatchAt(eq(group), eq(crimeAt), eq(killer), eq("Caller_911"), eq(0), eq(""), anyMap());
	}

	// ── a live 911 call (PLAN.md 1.5, review r2: one landing path) ────────

	@Test
	@DisplayName("a live 911 call commits as a CIVILIAN report with its viewers, tips the squad to the crime spot and "
	             + "dispatch says Caller_911 there with the crime by name")
	void liveCall_commitsCivilian_tipsCrimeSpot_radiosCaller911() {
		reports.landCivilian(killer, Crimes.CAR_THEFT, crimeAt, 3);

		InOrder order = inOrder(ledger, crimes, copManager, radio);
		order.verify(ledger).commitReported(eq(killer), eq(ReportKind.CIVILIAN), any());
		order.verify(crimes).commit(killer, Crimes.CAR_THEFT, crimeAt, false, 3);
		order.verify(copManager).groupOf(killerId);
		// R4: the call is a tip to the crime spot, not a sighting
		assertFalse(squad.hasFreshSighting());
		assertEquals(crimeAt, squad.lastKnownLocation());
		// %place% is placeOf(crimeAt) inside dispatchAt; %suspect% belongs to the CopRadio extras hook (disguise lane)
		order.verify(radio).dispatchAt(group, crimeAt, killer, "Caller_911", 0, "", Map.of("crime", "Car Theft"));
		assertTrue(debugContains("REPORT land Crook crime=Car_Theft reporter=CIVILIAN witnesses=3 at=1,64,1"),
		           debug::toString);
	}

	@Test
	@DisplayName("review r2: a live 911 call while a cop has him in sight keeps the squad's sighting")
	void liveCall_squadInContact_keepsTheSighting() {
		Location live = new Location(world, 10, 64, 10);
		squad.reportSighting(live);

		reports.landCivilian(killer, Crimes.CAR_THEFT, crimeAt, 1);

		assertEquals(live, squad.lastKnownLocation());
		verify(radio).dispatchAt(group, crimeAt, killer, "Caller_911", 0, "", Map.of("crime", "Car Theft"));
	}

	@Test
	@DisplayName("review r2: a live 911 call on a player cuffed during the call commits nothing")
	void liveCall_restrained_drops() {
		when(detainment.isRestrained(killer)).thenReturn(true);

		reports.landCivilian(killer, Crimes.CAR_THEFT, crimeAt, 2);

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(radio, never()).dispatchAt(any(), any(), any(), anyString(), anyInt(), anyString(), anyMap());
		assertTrue(debugContains("REPORT drop Crook crime=Car_Theft reporter=CIVILIAN witnesses=2 at=1,64,1 "
		                         + "reason=restrained"), debug::toString);
	}

	@Test
	@DisplayName("a live 911 call that raises no group (no star) or is refused says nothing on the radio")
	void liveCall_noGroupOrRefused_noRadio() {
		when(copManager.groupOf(killerId)).thenReturn(null);
		reports.landCivilian(killer, Crimes.CAR_THEFT, crimeAt, 1);
		verify(crimes).commit(killer, Crimes.CAR_THEFT, crimeAt, false, 1);

		when(copManager.groupOf(killerId)).thenReturn(group);
		when(crimes.commit(any(), anyString(), any(), anyBoolean(), anyInt())).thenReturn(false);
		reports.landCivilian(killer, Crimes.CAR_THEFT, crimeAt, 1);

		verify(radio, never()).dispatchAt(any(), any(), any(), anyString(), anyInt(), anyString(), anyMap());
		assertNull(squad.lastKnownLocation());
		assertTrue(debugContains("reason=refused"), debug::toString);
	}

	// ── lifecycle (PLAN.md 1.4) ─────────────────────────────────────────────

	@Test
	@DisplayName("a reload restarts the 10-tick task and keeps the reports; a shutdown stops it and drops everything")
	void shutdown_cancelsTimer_reload_keepsCases() {
		CopNpc bob = cop("Bob", 10, 90f);
		reports.onInitialize(true);
		assertEquals(1, timers.size());
		open(Crimes.KILL_COP, UUID.randomUUID());

		reports.onPreClear();
		reports.onInitialize(false);
		assertEquals(1, stopped[0]);
		assertEquals(2, timers.size());
		assertEquals(1, reports.pending(killerId));

		reports.defer(killer, Crimes.CAR_THEFT, crimeAt, 1, ReportKind.CIVILIAN);
		reports.onShutdown();
		assertEquals(2, stopped[0]);
		assertEquals(0, reports.pending(killerId));
		verify(bob.getEntity()).setGlowing(false);
		reports.onJoin(killer);
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("the timer task is the tick: running it lands a due report")
	void timerTask_runsTheTick() {
		cop("Bob", 10, 90f);
		reports.onInitialize(true);
		open(Crimes.KILL_COP, UUID.randomUUID());

		advanceSeconds(3);
		timers.get(0).run();

		verify(crimes).commit(killer, Crimes.KILL_COP, crimeAt, true, 1);
	}

	@Test
	@DisplayName("a killer offline at the tick (no quit seen yet) is deferred, never committed while away")
	void offlineAtTick_defers() {
		cop("Bob", 10, 90f);
		open(Crimes.KILL_COP, UUID.randomUUID());
		when(killer.isOnline()).thenReturn(false);

		advanceSeconds(3);
		reports.tick();
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());

		when(killer.isOnline()).thenReturn(true);
		reports.onJoin(killer);
		verify(crimes).commit(killer, Crimes.KILL_COP, crimeAt, true, 1);
	}
}
