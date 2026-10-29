package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.crime.Crimes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WitnessService - civilians who see an unwitnessed crime call 911 (P-WIT-1..3, 6)")
class WitnessServiceTest {

	private final WitnessTestKit kit = new WitnessTestKit();

	// ── who calls ────────────────────────────────────────────────────────────

	@Test
	@DisplayName("three civilians, two see the crime: the nearest one who sees calls, the other watches, 1 caller")
	void nearestSeeingCivilianCalls() {
		CivilianNpc blind   = kit.civilian(3, false);
		CivilianNpc nearest = kit.civilian(10, true);
		CivilianNpc farther = kit.civilian(18, true);

		assertEquals(1, kit.witness("Car_Theft"));

		assertEquals(CivilianState.CALLING, nearest.getCurrentState());
		assertEquals(CivilianState.IDLE, farther.getCurrentState());
		assertEquals(CivilianState.IDLE, blind.getCurrentState());
		WitnessCall call = nearest.getWitnessCall();
		assertSame(nearest, call.caller());
		assertEquals(java.util.List.of(farther), call.watchers());
		assertEquals("Car_Theft", call.crimeId());
		assertTrue(call.glow());
		assertEquals(1, kit.service.activeCalls().size());
	}

	@Test
	@DisplayName("the criminal sees the Witness_Calling countdown (caller, Call_Seconds) and the WITNESS debug line is written")
	void criminalIsToldAndDebugLogged() {
		kit.civilian(10, true);

		kit.witness("Car_Theft");

		assertEquals(java.util.List.of("Bob|CALLING Pedestrian 8"), kit.notices);
		assertTrue(kit.debugContains("WITNESS Bob crime=Car_Theft callers=1"), kit.debug::toString);
	}

	@Test
	@DisplayName("nobody sees it: 0 callers, nobody CALLING, no message to the criminal (P-WIT-4)")
	void noOneSeesReturnsZero() {
		CivilianNpc a = kit.civilian(5, false);
		CivilianNpc b = kit.civilian(9, false);

		assertEquals(0, kit.witness("Car_Theft"));

		assertEquals(CivilianState.IDLE, a.getCurrentState());
		assertEquals(CivilianState.IDLE, b.getCurrentState());
		assertTrue(kit.notices.isEmpty());
		assertTrue(kit.service.activeCalls().isEmpty());
		assertTrue(kit.debugContains("callers=0"), kit.debug::toString);
	}

	@Test
	@DisplayName("a civilian beyond Witness.Range does not count even if the sight test says yes")
	void outOfRangeCivilianIgnored() {
		CivilianNpc far = kit.civilian(30, true);

		assertEquals(0, kit.witness("Car_Theft"));
		assertEquals(CivilianState.IDLE, far.getCurrentState());
	}

	@Test
	@DisplayName("civilians already FLEEING, in COMBAT or CALLING are not picked")
	void busyCiviliansExcluded() {
		CivilianNpc fleeing = kit.civilian(4, true);
		CivilianNpc combat  = kit.civilian(5, true);
		CivilianNpc calling = kit.civilian(6, true);
		CivilianNpc free    = kit.civilian(20, true);
		fleeing.transitionTo(CivilianState.FLEEING);
		combat.transitionTo(CivilianState.COMBAT);
		calling.transitionTo(CivilianState.CALLING);

		assertEquals(1, kit.witness("Car_Theft"));

		assertEquals(CivilianState.CALLING, free.getCurrentState());
		assertSame(free, free.getWitnessCall().caller());
		assertNull(fleeing.getWitnessCall());
		assertNull(combat.getWitnessCall());
	}

	@Test
	@DisplayName("Witness.Enable false: no call is started")
	void disabledStartsNothing() {
		CivilianNpc npc = kit.civilian(5, true);
		kit.rules = new WitnessRules(false, 24.0, 8, true, 16.0, 12.0, true, 10);

		assertEquals(0, kit.witness("Car_Theft"));
		assertEquals(CivilianState.IDLE, npc.getCurrentState());
	}

	@Test
	@DisplayName("an offline criminal or a crime location with no world starts no call and throws nothing")
	void offlineCriminalAndNullWorld() {
		CivilianNpc npc = kit.civilian(5, true);

		when(kit.criminal.isOnline()).thenReturn(false);
		assertEquals(0, kit.witness("Car_Theft"));

		when(kit.criminal.isOnline()).thenReturn(true);
		assertEquals(0, kit.service.witness(kit.criminal, "Car_Theft", new Location(null, 0, 64, 0)));
		assertEquals(CivilianState.IDLE, npc.getCurrentState());
	}

	@Test
	@DisplayName("Caller_Glow false: the call says no glow")
	void callerGlowOff() {
		CivilianNpc npc = kit.civilian(5, true);
		kit.rules = new WitnessRules(true, 24.0, 8, false, 16.0, 12.0, true, 10);

		kit.witness("Car_Theft");

		assertFalse(npc.getWitnessCall().glow());
	}

	// ── the call lands ───────────────────────────────────────────────────────

	@Test
	@DisplayName("the call lands after Call_Seconds: one CivilianCalledInEvent, caller FLEEING, call cleared (P-WIT-2)")
	void callLandsAfterCallSeconds() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(7.9);
		kit.service.tickCaller(caller);
		assertEquals(0, kit.countEvents(CivilianCalledInEvent.class));
		assertEquals(CivilianState.CALLING, caller.getCurrentState());

		kit.advanceSeconds(0.1);
		kit.service.tickCaller(caller);
		kit.service.tickCaller(caller);

		assertEquals(1, kit.countEvents(CivilianCalledInEvent.class));
		CivilianCalledInEvent event = (CivilianCalledInEvent) kit.events.get(0);
		assertSame(kit.criminal, event.getCriminal());
		assertEquals("Car_Theft", event.getCrimeId());
		assertEquals(kit.at, event.getAt());
		assertSame(caller.getEntity(), event.getCaller());
		assertEquals(CivilianState.FLEEING, caller.getCurrentState());
		assertNull(caller.getWitnessCall());
		assertTrue(kit.service.activeCalls().isEmpty());
		assertTrue(kit.debugContains("CALL_IN Bob crime=Car_Theft caller=Pedestrian"), kit.debug::toString);
		WitnessTestKit.Transition fled = kit.lastTransition(caller, CivilianState.FLEEING);
		assertEquals(kit.at, fled.lastAttacker(), "flee origin is the criminal");
		assertNull(fled.call(), "the call is already cleared at the FLEEING transition");
	}

	@Test
	@DisplayName("T-540: the landed call counts its viewers - the caller plus every watcher still valid at landing")
	void calledInEvent_carriesCallerPlusWatchers() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.civilian(12, true);
		CivilianNpc killed = kit.civilian(14, true);
		kit.civilian(16, true);
		kit.witness("Car_Theft");

		// one watcher dies before the call lands: killing a witness lowers the head count
		kit.advanceSeconds(4);
		when(killed.isValid()).thenReturn(false);
		kit.advanceSeconds(4);
		kit.service.tickCaller(caller);

		assertEquals(1, kit.countEvents(CivilianCalledInEvent.class));
		assertEquals(3, ((CivilianCalledInEvent) kit.events.get(0)).getViewers());
	}

	@Test
	@DisplayName("T-549: a hostile civilian (Quartermaster, defender, gang member) never calls 911, even when nearest")
	void hostileFaction_neverCalls() {
		CivilianNpc hostile = kit.civilian(5, true);
		when(hostile.isHostile()).thenReturn(true);
		CivilianNpc pedestrian = kit.civilian(10, true);

		assertEquals(1, kit.witness("Car_Theft"));

		assertEquals(CivilianState.IDLE, hostile.getCurrentState());
		assertNull(hostile.getWitnessCall());
		assertEquals(CivilianState.CALLING, pedestrian.getCurrentState());
	}

	@Test
	@DisplayName("T-549: a lone hostile civilian who sees the crime starts no call")
	void hostileFaction_aloneStartsNothing() {
		CivilianNpc hostile = kit.civilian(5, true);
		when(hostile.isHostile()).thenReturn(true);

		assertEquals(0, kit.witness("Car_Theft"));
		assertEquals(CivilianState.IDLE, hostile.getCurrentState());
	}

	@Test
	@DisplayName("T-549: a hostile civilian in range is no watcher, so it adds no Per_Extra_Witness viewer")
	void hostileFaction_isNotAViewer() {
		CivilianNpc caller  = kit.civilian(10, true);
		CivilianNpc hostile = kit.civilian(12, true);
		when(hostile.isHostile()).thenReturn(true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(8);
		kit.service.tickCaller(caller);

		assertEquals(1, ((CivilianCalledInEvent) kit.events.get(0)).getViewers());
	}

	@Test
	@DisplayName("T-549: a loud crime never turns a hostile civilian toward the noise")
	void hostileFaction_notTurnedByLoudCrime() {
		CivilianNpc hostile = kit.civilian(10, true, 270f);
		when(hostile.isHostile()).thenReturn(true);
		kit.rules = withLoudCrimes(List.of("Kill_Civilian"));

		assertEquals(0, kit.witness("Kill_Civilian"));
		verify(hostile.getEntity(), never()).teleport(any(Location.class));
	}

	@Test
	@DisplayName("a caller whose type has Flee_On_Damage off goes back to IDLE when the call lands, not FLEEING")
	void callEnd_respectsFleeDisabled() {
		CivilianNpc caller = kit.civilian(10, true);
		when(WitnessTestKit.ai(caller).fleeEnabled()).thenReturn(false);
		kit.witness("Car_Theft");

		kit.advanceSeconds(8);
		kit.service.tickCaller(caller);

		assertEquals(1, kit.countEvents(CivilianCalledInEvent.class));
		assertEquals(CivilianState.IDLE, caller.getCurrentState());
		assertNull(kit.lastTransition(caller, CivilianState.FLEEING));
	}

	@Test
	@DisplayName("a scared caller whose type has Flee_On_Damage off goes back to IDLE, not FLEEING")
	void callStopped_respectsFleeDisabled() {
		CivilianNpc caller = kit.civilian(10, true);
		when(WitnessTestKit.ai(caller).fleeEnabled()).thenReturn(false);
		kit.witness("Car_Theft");

		kit.aiming = true;
		kit.service.tickCaller(caller);

		assertEquals(CivilianState.IDLE, caller.getCurrentState());
		assertNull(kit.lastTransition(caller, CivilianState.FLEEING));
	}

	@Test
	@DisplayName("T-540: a lone caller is one viewer")
	void calledInEvent_loneCallerIsOneViewer() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(8);
		kit.service.tickCaller(caller);

		assertEquals(1, ((CivilianCalledInEvent) kit.events.get(0)).getViewers());
	}

	@Test
	@DisplayName("a caller that is no longer valid when the call is due loses the call: no event, reason=lost")
	void invalidCallerAtLandingLosesCall() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");
		when(caller.isValid()).thenReturn(false);

		kit.advanceSeconds(8);
		kit.service.tickCaller(caller);

		assertEquals(0, kit.countEvents(CivilianCalledInEvent.class));
		assertNull(caller.getWitnessCall());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=lost"), kit.debug::toString);
	}

	@Test
	@DisplayName("ticking a civilian that has no call does nothing")
	void tickWithoutCallIsNoop() {
		CivilianNpc idle = kit.civilian(5, false);

		kit.service.tickCaller(idle);

		assertEquals(CivilianState.IDLE, idle.getCurrentState());
		assertTrue(kit.events.isEmpty());
	}

	// ── the player stops the call ────────────────────────────────────────────

	@Test
	@DisplayName("hitting the caller at 4 s cancels: it flees, no event at 8 s, reason=hit (P-WIT-3)")
	void hittingCallerCancels() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(4);
		kit.service.onPlayerHitCivilian(kit.criminal, caller);
		kit.advanceSeconds(4);
		kit.service.tickCaller(caller);

		assertEquals(0, kit.countEvents(CivilianCalledInEvent.class));
		assertEquals(CivilianState.FLEEING, caller.getCurrentState());
		assertNull(caller.getWitnessCall());
		assertTrue(kit.service.activeCalls().isEmpty());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=hit"), kit.debug::toString);
		WitnessTestKit.Transition fled = kit.lastTransition(caller, CivilianState.FLEEING);
		assertEquals(kit.at, fled.lastAttacker(), "flee origin is the criminal");
		assertNull(fled.call(), "the call is already cleared at the FLEEING transition");
	}

	@Test
	@DisplayName("aiming a weapon at the caller cancels: it flees, watchers do not take over, reason=scared")
	void aimingWeaponScaresCaller() {
		CivilianNpc caller  = kit.civilian(10, true);
		CivilianNpc watcher = kit.civilian(15, true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(2);
		kit.aiming = true;
		kit.service.tickCaller(caller);
		kit.advanceSeconds(10);
		kit.service.tickCaller(watcher);

		assertEquals(CivilianState.FLEEING, caller.getCurrentState());
		assertEquals(CivilianState.IDLE, watcher.getCurrentState());
		assertEquals(0, kit.countEvents(CivilianCalledInEvent.class));
		assertTrue(kit.service.activeCalls().isEmpty());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=scared"), kit.debug::toString);
		WitnessTestKit.Transition fled = kit.lastTransition(caller, CivilianState.FLEEING);
		assertEquals(kit.at, fled.lastAttacker(), "flee origin is the criminal");
		assertNull(fled.call(), "the call is already cleared at the FLEEING transition");
	}

	@Test
	@DisplayName("not aiming: the call keeps running through the scare poll")
	void notAimingKeepsCalling() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(3);
		kit.service.tickCaller(caller);

		assertEquals(CivilianState.CALLING, caller.getCurrentState());
		assertEquals(1, kit.service.activeCalls().size());
	}

	@Test
	@DisplayName("the caller dies at 5 s: a watcher that sees the criminal restarts a full call, event 8 s after")
	void killedCallerHandsOverToWatcher() {
		CivilianNpc caller  = kit.civilian(10, true);
		CivilianNpc watcher = kit.civilian(15, true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(5);
		when(caller.isValid()).thenReturn(false);
		kit.sighted.remove(caller);
		kit.service.onCallerDeath(caller);

		assertEquals(CivilianState.CALLING, watcher.getCurrentState());
		assertSame(watcher, watcher.getWitnessCall().caller());
		assertEquals(5000L, watcher.getWitnessCall().startedAtMillis());

		kit.advanceSeconds(7.9);
		kit.service.tickCaller(watcher);
		assertEquals(0, kit.countEvents(CivilianCalledInEvent.class));

		kit.advanceSeconds(0.1);
		kit.service.tickCaller(watcher);
		assertEquals(1, kit.countEvents(CivilianCalledInEvent.class));
		assertSame(watcher.getEntity(), ((CivilianCalledInEvent) kit.events.get(0)).getCaller());
	}

	@Test
	@DisplayName("the caller dies and no watcher still sees the criminal: the call is lost, no new caller")
	void killedCallerWithNoWatcherCancels() {
		CivilianNpc caller  = kit.civilian(10, true);
		CivilianNpc watcher = kit.civilian(15, true);
		kit.witness("Car_Theft");

		when(caller.isValid()).thenReturn(false);
		kit.sighted.clear();
		kit.service.onCallerDeath(caller);

		assertEquals(CivilianState.IDLE, watcher.getCurrentState());
		assertTrue(kit.service.activeCalls().isEmpty());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=killed"), kit.debug::toString);
	}

	@Test
	@DisplayName("0.21.2 T-551: the criminal quits with the caller alive: the call is deferred, never forgiven - one "
	             + "deferred CivilianCalledInEvent (caller + live watchers), the caller back to IDLE, nothing said")
	void criminalQuit_defersCall() {
		CivilianNpc caller  = kit.civilian(10, true);
		CivilianNpc watcher = kit.civilian(15, true);
		kit.witness("Car_Theft");

		kit.service.onCriminalQuit(kit.criminalId);

		assertTrue(kit.service.activeCalls().isEmpty());
		assertNull(caller.getWitnessCall());
		assertEquals(CivilianState.IDLE, caller.getCurrentState());
		assertEquals(CivilianState.IDLE, watcher.getCurrentState());
		assertEquals(1, kit.countEvents(CivilianCalledInEvent.class));
		CivilianCalledInEvent event = (CivilianCalledInEvent) kit.events.get(kit.events.size() - 1);
		assertTrue(event.isDeferred());
		assertEquals(2, event.getViewers());
		assertEquals("Car_Theft", event.getCrimeId());
		assertTrue(kit.debugContains("CALL_DEFER Bob crime=Car_Theft viewers=2"), kit.debug::toString);
	}

	@Test
	@DisplayName("0.21.2 T-551: a criminal who went offline between ticks defers the call on the next tick")
	void offlineCriminalAtTick_defersCall() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		when(kit.criminal.isOnline()).thenReturn(false);
		kit.advanceSeconds(1);
		kit.service.tickCaller(caller);

		assertEquals(1, kit.countEvents(CivilianCalledInEvent.class));
		assertTrue(((CivilianCalledInEvent) kit.events.get(kit.events.size() - 1)).isDeferred());
		assertTrue(kit.service.activeCalls().isEmpty());
	}

	@Test
	@DisplayName("0.21.2: loudWithin is the civilians' own shot clock - a gun within Loud_Shot_Seconds, never a melee swing")
	void loudWithin_isTheShotClock() {
		assertFalse(kit.service.loudWithin(kit.criminal));

		kit.service.heardShot(kit.criminal, "MELEE", 0L);
		assertFalse(kit.service.loudWithin(kit.criminal), "a melee swing is silent");

		kit.service.heardShot(kit.criminal, "GUN", 0L);
		assertTrue(kit.service.loudWithin(kit.criminal));
		kit.advanceSeconds(3);
		assertTrue(kit.service.loudWithin(kit.criminal), "the edge of Loud_Shot_Seconds (3) is still loud");
		kit.advanceSeconds(0.1);
		assertFalse(kit.service.loudWithin(kit.criminal));
	}

	// ── Assault_Civilian (P-WIT-6) and the victim ────────────────────────────

	@Test
	@DisplayName("two hits inside 10 s are one Assault_Civilian; the hit at 11 s is a second")
	void assaultCivilianThrottled() {
		CivilianNpc victim = kit.civilian(6, false);

		kit.service.onPlayerHitCivilian(kit.criminal, victim);
		kit.advanceSeconds(5);
		kit.service.onPlayerHitCivilian(kit.criminal, victim);
		verify(kit.crimes, times(1)).commit(eq(kit.criminal), eq(Crimes.ASSAULT_CIVILIAN), any(Location.class));

		kit.advanceSeconds(6);
		kit.service.onPlayerHitCivilian(kit.criminal, victim);
		verify(kit.crimes, times(2)).commit(eq(kit.criminal), eq(Crimes.ASSAULT_CIVILIAN), any(Location.class));
	}

	@Test
	@DisplayName("the throttle is per civilian: two different victims are two crimes")
	void assaultThrottleIsPerVictim() {
		CivilianNpc one = kit.civilian(6, false);
		CivilianNpc two = kit.civilian(8, false);

		kit.service.onPlayerHitCivilian(kit.criminal, one);
		kit.service.onPlayerHitCivilian(kit.criminal, two);

		verify(kit.crimes, times(2)).commit(eq(kit.criminal), eq(Crimes.ASSAULT_CIVILIAN), any(Location.class));
	}

	@Test
	@DisplayName("Witness.Report_Assault false: hitting a civilian publishes nothing")
	void reportAssaultOffPublishesNothing() {
		CivilianNpc victim = kit.civilian(6, false);
		kit.rules = new WitnessRules(true, 24.0, 8, true, 16.0, 12.0, false, 10);

		kit.service.onPlayerHitCivilian(kit.criminal, victim);

		verify(kit.crimes, never()).commit(any(), any(), any(), anyBoolean(), anyInt());
		verify(kit.crimes, never()).commit(any(), any(), any());
	}

	@Test
	@DisplayName("the civilian who was just hit is never the witness of that assault, the next one in range is")
	void victimIsNeverItsOwnWitness() {
		CivilianNpc victim   = kit.civilian(4, true);
		CivilianNpc onlooker = kit.civilian(12, true);
		org.mockito.Mockito.doAnswer(invocation -> {
			kit.service.witness(kit.criminal, Crimes.ASSAULT_CIVILIAN, kit.at);
			return true;
		}).when(kit.crimes).commit(any(), eq(Crimes.ASSAULT_CIVILIAN), any(Location.class));

		kit.service.onPlayerHitCivilian(kit.criminal, victim);

		assertNull(victim.getWitnessCall());
		assertSame(onlooker, onlooker.getWitnessCall().caller());
	}

	@Test
	@DisplayName("the victim exclusion lasts 2 s: afterwards the same civilian may witness another crime")
	void victimExclusionExpires() {
		CivilianNpc victim = kit.civilian(4, true);
		kit.service.onPlayerHitCivilian(kit.criminal, victim);

		kit.advanceSeconds(2.5);

		assertEquals(1, kit.witness("Car_Theft"));
		assertSame(victim, victim.getWitnessCall().caller());
	}

	// ── lethal hit and orphaned calls (revision 1) ───────────────────────────

	@Test
	@DisplayName("a lethal hit on the caller keeps the call attached so the death hands it to a watcher (D-WIT-6)")
	void lethalHitKeepsCallForHandover() {
		CivilianNpc caller  = kit.civilian(10, true);
		CivilianNpc watcher = kit.civilian(15, true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(5);
		kit.service.onPlayerHitCivilian(kit.criminal, caller, true);

		assertEquals(CivilianState.CALLING, caller.getCurrentState());
		assertSame(caller, caller.getWitnessCall().caller());
		assertEquals(1, kit.service.activeCalls().size());
		assertNull(kit.lastTransition(caller, CivilianState.FLEEING));

		when(caller.isValid()).thenReturn(false);
		kit.sighted.remove(caller);
		kit.service.onCallerDeath(caller);

		assertEquals(CivilianState.CALLING, watcher.getCurrentState());
		assertSame(watcher, watcher.getWitnessCall().caller());
		assertEquals(5000L, watcher.getWitnessCall().startedAtMillis());
	}

	@Test
	@DisplayName("a lethal hit publishes no Assault_Civilian (Kill_Civilian covers the kill)")
	void lethalHitPublishesNoAssault() {
		CivilianNpc victim = kit.civilian(6, false);

		kit.service.onPlayerHitCivilian(kit.criminal, victim, true);

		verify(kit.crimes, never()).commit(any(), any(), any());
		verify(kit.crimes, never()).commit(any(), any(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("dropCall forgets a call the shipped code walked away from: gone from the map and the npc, no transition")
	void dropCallNeverTransitions() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");
		int before = kit.transitions.size();

		kit.service.dropCall(caller);

		assertNull(caller.getWitnessCall());
		assertTrue(kit.service.activeCalls().isEmpty());
		assertEquals(before, kit.transitions.size(), "dropCall must not transition");
		assertEquals(CivilianState.CALLING, caller.getCurrentState());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=lost"), kit.debug::toString);
	}

	@Test
	@DisplayName("judge-fix r1: a caller whose entity is gone does not leave its call behind when the criminal quits")
	void destroyedCallerCallRemovedOnQuit() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");
		when(caller.getEntity()).thenReturn(null);

		kit.service.onCriminalQuit(kit.criminalId);

		assertTrue(kit.service.activeCalls().isEmpty());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=logout"), kit.debug::toString);
	}

	// ── 0.21.1: the four visible moments (CIVILIAN-911-DESIGN §3.1) ──────────

	@Test
	@DisplayName("Notice: the caller speaks Witness_Start in Witness_Format, addressed to the criminal")
	void callStart_speaksWitnessStartToCriminal() {
		kit.civilian(10, true);

		kit.witness("Car_Theft");

		assertEquals(List.of("Pedestrian|Witness_Start|Witness_Format|Bob|{}"), kit.speech);
	}

	@Test
	@DisplayName("Dialling: the countdown is sent once per whole second, 8 down to 1, naming the caller")
	void countdown_actionBarShowsCallerAndSeconds() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		for (int i = 0; i < 15; i++) {
			kit.advanceSeconds(0.5);
			kit.service.tickCaller(caller);
		}

		assertEquals(List.of("Bob|CALLING Pedestrian 8", "Bob|CALLING Pedestrian 7", "Bob|CALLING Pedestrian 6",
		                     "Bob|CALLING Pedestrian 5", "Bob|CALLING Pedestrian 4", "Bob|CALLING Pedestrian 3",
		                     "Bob|CALLING Pedestrian 2", "Bob|CALLING Pedestrian 1"), kit.notices);
	}

	@Test
	@DisplayName("Stopped (scared): the caller speaks Witness_Scared and the criminal is told Witness_Stopped")
	void scaredCaller_speaksScaredAndToldStopped() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		kit.aiming = true;
		kit.service.tickCaller(caller);

		assertTrue(kit.speech.contains("Pedestrian|Witness_Scared|Witness_Format|Bob|{}"), kit.speech::toString);
		assertEquals("Bob|STOPPED", kit.notices.get(kit.notices.size() - 1));
	}

	@Test
	@DisplayName("Stopped (hit): the hit caller speaks Witness_Scared too")
	void hitCaller_speaksScared() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		kit.service.onPlayerHitCivilian(kit.criminal, caller);

		assertTrue(kit.speech.contains("Pedestrian|Witness_Scared|Witness_Format|Bob|{}"), kit.speech::toString);
		assertEquals("Bob|STOPPED", kit.notices.get(kit.notices.size() - 1));
	}

	@Test
	@DisplayName("a logout says nothing and tells nothing")
	void logoutIsSilent() {
		kit.civilian(10, true);
		kit.witness("Car_Theft");

		kit.service.onCriminalQuit(kit.criminalId);

		assertEquals(List.of("Pedestrian|Witness_Start|Witness_Format|Bob|{}"), kit.speech);
		assertFalse(kit.notices.contains("Bob|STOPPED"));
	}

	@Test
	@DisplayName("Connected: Witness_Landed (with %place%) is spoken before the CivilianCalledInEvent is fired")
	void landing_speaksLandedBeforeCalledInEvent() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");
		List<Integer> eventsAtLanding = new java.util.ArrayList<>();
		org.mockito.Mockito.doAnswer(invocation -> {
			if ("Witness_Landed".equals(invocation.getArgument(1))) eventsAtLanding.add(kit.events.size());
			kit.speech.add(invocation.getArgument(1) + "|" + invocation.getArgument(4));
			return null;
		}).when(kit.civilians).shout(any(), anyString(), anyString(), any(), any());

		kit.advanceSeconds(8);
		kit.service.tickCaller(caller);

		assertEquals(List.of(0), eventsAtLanding);
		assertTrue(kit.speech.contains("Witness_Landed|{place=here}"), kit.speech::toString);
		assertEquals(1, kit.countEvents(CivilianCalledInEvent.class));
	}

	// ── 0.21.1: intimidation (right-click) ───────────────────────────────────

	@Test
	@DisplayName("the criminal intimidates the caller within Intimidate_Range: cancelled INTIMIDATED, it flees, speaks")
	void intimidate_byCriminalInRange_cancelsAndFlees() {
		CivilianNpc caller = kit.civilian(3, true);
		kit.witness("Car_Theft");

		assertTrue(kit.service.intimidate(caller, kit.criminal));

		assertNull(caller.getWitnessCall());
		assertEquals(CivilianState.FLEEING, caller.getCurrentState());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=intimidated"), kit.debug::toString);
		assertTrue(kit.speech.contains("Pedestrian|Witness_Scared|Witness_Format|Bob|{}"), kit.speech::toString);
		assertEquals("Bob|STOPPED", kit.notices.get(kit.notices.size() - 1));
	}

	@Test
	@DisplayName("a stranger, the criminal out of range, or Intimidate_Range 0 cannot stop the call")
	void intimidate_byStranger_orOutOfRange_keepsCalling() {
		CivilianNpc near = kit.civilian(3, true);
		kit.witness("Car_Theft");
		org.bukkit.entity.Player stranger = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
		when(stranger.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
		when(stranger.getLocation()).thenReturn(kit.at.clone());

		assertFalse(kit.service.intimidate(near, stranger));
		kit.rules = withIntimidateRange(0);
		assertFalse(kit.service.intimidate(near, kit.criminal));
		kit.rules = withIntimidateRange(2);
		assertFalse(kit.service.intimidate(near, kit.criminal));

		assertEquals(CivilianState.CALLING, near.getCurrentState());
		assertEquals(1, kit.service.activeCalls().size());
	}

	@Test
	@DisplayName("intimidating a civilian who is not calling does nothing")
	void intimidate_nonCaller_isIgnored() {
		CivilianNpc idle = kit.civilian(3, true);

		assertFalse(kit.service.intimidate(idle, kit.criminal));
		assertEquals(CivilianState.IDLE, idle.getCurrentState());
	}

	// ── 0.21.1: the 180 degree view cone and loud crimes ─────────────────────

	@Test
	@DisplayName("View_Angle 180: a civilian with its back to the criminal is not a witness (0 callers)")
	void civilianFacingAway_isNotAWitness() {
		CivilianNpc away = kit.civilian(10, true, 270f); // looks east (+X), the criminal is west of it

		assertEquals(0, kit.witness("Car_Theft"));
		assertEquals(CivilianState.IDLE, away.getCurrentState());
	}

	@Test
	@DisplayName("View_Angle 360 keeps the old behaviour: a civilian facing away still calls")
	void viewAngle360_keepsOldBehaviour() {
		CivilianNpc away = kit.civilian(10, true, 270f);
		kit.rules = withViewAngle(360);

		assertEquals(1, kit.witness("Car_Theft"));
		assertEquals(CivilianState.CALLING, away.getCurrentState());
	}

	@Test
	@DisplayName("the cone is half View_Angle each side: 80 degrees off is seen at 180, unseen at 120")
	void coneEdge() {
		Location civilian = new Location(kit.world, 10, 64, 0, 90f, 0f); // looks west, straight at the origin
		Location left80   = new Location(kit.world, 10 - Math.cos(Math.toRadians(80)), 64, Math.sin(Math.toRadians(80)));

		assertTrue(WitnessService.inView(civilian, left80, 180));
		assertFalse(WitnessService.inView(civilian, left80, 120));
		assertTrue(WitnessService.inView(civilian, new Location(kit.world, 30, 64, 0), 360));
		assertFalse(WitnessService.inView(civilian, new Location(kit.world, 30, 64, 0), 180));
	}

	@Test
	@DisplayName("a loud crime (Loud_Crimes) turns every civilian in range to the noise first, so one facing away calls")
	void loudCrime_turnsCiviliansToTheNoise() {
		CivilianNpc away  = kit.civilian(10, true, 270f);
		CivilianNpc blind = kit.civilian(14, false, 270f);
		kit.rules = withLoudCrimes(List.of("Kill_Civilian"));

		assertEquals(1, kit.witness("Kill_Civilian"));

		assertEquals(CivilianState.CALLING, away.getCurrentState());
		for (CivilianNpc npc : List.of(away, blind)) {
			org.mockito.ArgumentCaptor<Location> turned = org.mockito.ArgumentCaptor.forClass(Location.class);
			verify(npc.getEntity()).teleport(turned.capture());
			double yaw = ((turned.getValue().getYaw() % 360.0) + 360.0) % 360.0;
			assertEquals(90.0, yaw, 1.0, "turned west, to the crime");
		}
	}

	@Test
	@DisplayName("shipped Loud_Crimes is empty: a fist or melee kill is silent, so a civilian behind the killer does not call")
	void shippedKillIsSilent() {
		CivilianNpc away = kit.civilian(10, true, 270f);
		kit.rules = WitnessRules.DEFAULT;

		assertEquals(0, kit.witness("Kill_Civilian"));
		assertEquals(CivilianState.IDLE, away.getCurrentState());
		verify(away.getEntity(), never()).teleport(any(Location.class));
	}

	@Test
	@DisplayName("a silent crime turns nobody")
	void silentCrime_turnsNobody() {
		CivilianNpc away = kit.civilian(10, true, 270f);

		kit.witness("Car_Theft");

		verify(away.getEntity(), never()).teleport(any(Location.class));
	}

	@Test
	@DisplayName("a crime within Loud_Shot_Seconds of the criminal's own gunshot is loud; a later one is silent")
	void crimeRightAfterAGunshotIsLoud() {
		CivilianNpc away = kit.civilian(10, true, 270f);
		kit.service.heardShot(kit.criminal, "GUN", 0L);

		kit.advanceSeconds(2);
		assertEquals(1, kit.witness("Car_Theft"));
		assertEquals(CivilianState.CALLING, away.getCurrentState());

		kit.service.onCriminalQuit(kit.criminalId);
		kit.advanceSeconds(5);
		assertEquals(0, kit.witness("Car_Theft"));
	}

	// ── 0.21.1: Scared_Cooldown_Seconds ──────────────────────────────────────

	@Test
	@DisplayName("a civilian the criminal stopped (scared) cannot call again before Scared_Cooldown_Seconds")
	void scaredCiviliansWaitBeforeCallingAgain() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");
		kit.aiming = true;
		kit.service.tickCaller(caller);
		kit.aiming = false;
		caller.transitionTo(CivilianState.IDLE);

		kit.advanceSeconds(59);
		assertEquals(0, kit.witness("Car_Theft"));

		kit.advanceSeconds(1);
		assertEquals(1, kit.witness("Car_Theft"));
	}

	@Test
	@DisplayName("an intimidated civilian waits out the cooldown too; a landed caller does not")
	void intimidatedWaitsLandedDoesNot() {
		CivilianNpc caller = kit.civilian(3, true);
		kit.witness("Car_Theft");
		kit.service.intimidate(caller, kit.criminal);
		caller.transitionTo(CivilianState.IDLE);
		kit.advanceSeconds(10);
		assertEquals(0, kit.witness("Car_Theft"));

		kit.advanceSeconds(60);
		assertEquals(1, kit.witness("Car_Theft"));
		kit.advanceSeconds(8);
		kit.service.tickCaller(caller);
		caller.transitionTo(CivilianState.IDLE);
		assertEquals(1, kit.witness("Car_Theft"));
	}

	// ── 0.21.1: hand-over ────────────────────────────────────────────────────

	@Test
	@DisplayName("hand-over: the watcher who takes the call speaks its own Witness_Start")
	void handOver_newCallerSpeaksStart() {
		CivilianNpc caller  = kit.civilian(10, true);
		CivilianNpc watcher = kit.civilian(15, true);
		kit.witness("Car_Theft");
		kit.speech.clear();

		when(caller.isValid()).thenReturn(false);
		kit.sighted.remove(caller);
		kit.service.onCallerDeath(caller);

		assertSame(watcher, watcher.getWitnessCall().caller());
		assertEquals(List.of("Pedestrian|Witness_Start|Witness_Format|Bob|{}"), kit.speech);
	}

	@Test
	@DisplayName("hand-over skips a watcher already CALLING (the gated kill started its own call): no call is overwritten")
	void handOver_skipsWatcherAlreadyCalling() {
		CivilianNpc caller  = kit.civilian(10, true);
		CivilianNpc watcher = kit.civilian(15, true);
		CivilianNpc other   = kit.civilian(20, true);
		kit.witness("Car_Theft");
		when(caller.isValid()).thenReturn(false);
		kit.sighted.remove(caller);
		// the kill of the caller is a gated Kill_Civilian: the nearest free civilian (the watcher) starts that call first
		assertEquals(1, kit.witness("Kill_Civilian"));
		WitnessCall killCall = watcher.getWitnessCall();
		assertEquals("Kill_Civilian", killCall.crimeId());

		kit.service.onCallerDeath(caller);

		assertSame(killCall, watcher.getWitnessCall());
		assertSame(other, other.getWitnessCall().caller());
		assertEquals("Car_Theft", other.getWitnessCall().crimeId());
	}

	@Test
	@DisplayName("Kill_Civilian: the civilian just killed is never its own witness")
	void killedCivilian_isNotItsOwnWitness() {
		CivilianNpc victim   = kit.civilian(4, true);
		CivilianNpc onlooker = kit.civilian(12, true);

		kit.service.onPlayerHitCivilian(kit.criminal, victim, true);
		assertEquals(1, kit.witness("Kill_Civilian"));

		assertNull(victim.getWitnessCall());
		assertSame(onlooker, onlooker.getWitnessCall().caller());
	}

	private static WitnessRules withViewAngle(double angle) {
		WitnessRules d = WitnessRules.DEFAULT;
		return new WitnessRules(d.enabled(), d.range(), d.callSeconds(), d.callerGlow(), d.scareRange(), d.scareAngle(),
		                        d.reportAssault(), d.assaultRepeatSeconds(), angle, d.noticeSeconds(),
		                        d.noticeParticle(), d.phoneItem(), d.intimidateRange(), d.scaredCooldownSeconds(),
		                        d.loudCrimes(), d.loudShotSeconds(), d.loudWeaponTypes());
	}

	private static WitnessRules withIntimidateRange(double range) {
		WitnessRules d = WitnessRules.DEFAULT;
		return new WitnessRules(d.enabled(), d.range(), d.callSeconds(), d.callerGlow(), d.scareRange(), d.scareAngle(),
		                        d.reportAssault(), d.assaultRepeatSeconds(), d.viewAngle(), d.noticeSeconds(),
		                        d.noticeParticle(), d.phoneItem(), range, d.scaredCooldownSeconds(), d.loudCrimes(),
		                        d.loudShotSeconds(), d.loudWeaponTypes());
	}

	private static WitnessRules withLoudCrimes(List<String> loud) {
		WitnessRules d = WitnessRules.DEFAULT;
		return new WitnessRules(d.enabled(), d.range(), d.callSeconds(), d.callerGlow(), d.scareRange(), d.scareAngle(),
		                        d.reportAssault(), d.assaultRepeatSeconds(), d.viewAngle(), d.noticeSeconds(),
		                        d.noticeParticle(), d.phoneItem(), d.intimidateRange(), d.scaredCooldownSeconds(), loud,
		                        d.loudShotSeconds(), d.loudWeaponTypes());
	}
}
