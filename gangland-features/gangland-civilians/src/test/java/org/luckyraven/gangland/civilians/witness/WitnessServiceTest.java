package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.crime.Crimes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
	@DisplayName("the criminal sees the Witness_Calling line once and the WITNESS debug line is written")
	void criminalIsToldAndDebugLogged() {
		kit.civilian(10, true);

		kit.witness("Car_Theft");

		assertEquals(java.util.List.of("Bob|WITNESS-CALLING"), kit.notices);
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
	@DisplayName("the criminal quits: every call about them is cancelled and the caller goes back to IDLE")
	void criminalQuitCancels() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		kit.service.onCriminalQuit(kit.criminalId);

		assertTrue(kit.service.activeCalls().isEmpty());
		assertNull(caller.getWitnessCall());
		assertEquals(CivilianState.IDLE, caller.getCurrentState());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=logout"), kit.debug::toString);
	}

	@Test
	@DisplayName("a criminal who went offline between ticks cancels the call on the next tick")
	void offlineCriminalAtTickCancels() {
		CivilianNpc caller = kit.civilian(10, true);
		kit.witness("Car_Theft");

		when(kit.criminal.isOnline()).thenReturn(false);
		kit.advanceSeconds(8);
		kit.service.tickCaller(caller);

		assertEquals(0, kit.countEvents(CivilianCalledInEvent.class));
		assertTrue(kit.service.activeCalls().isEmpty());
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
}
