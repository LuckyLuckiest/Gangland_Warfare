package org.luckyraven.gangland.civilians.npc.state.behavior;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessTestKit;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("CivilianCallingBehavior - the caller stands, faces away and glows while 911 rings")
class CivilianCallingBehaviorTest {

	private final WitnessTestKit          kit      = new WitnessTestKit();
	private final CivilianCallingBehavior behavior = new CivilianCallingBehavior();

	@Test
	@DisplayName("entering stops navigation and, with Caller_Glow, makes the caller glow")
	void enterStopsAndGlows() {
		CivilianNpc npc = kit.civilian(10, true);
		kit.witness("Car_Theft");

		behavior.onEnter(npc);

		verify(npc).stopNavigation();
		verify(npc.getEntity()).setGlowing(true);
	}

	@Test
	@DisplayName("entering without Caller_Glow leaves the caller unlit")
	void enterWithoutGlow() {
		kit.rules = new org.luckyraven.gangland.civilians.witness.WitnessRules(true, 24.0, 8, false, 16.0, 12.0, true, 10);
		CivilianNpc npc = kit.civilian(10, true);
		kit.witness("Car_Theft");

		behavior.onEnter(npc);

		verify(npc.getEntity(), never()).setGlowing(true);
	}

	@Test
	@DisplayName("entering turns the caller to face away from the criminal")
	void enterFacesAway() {
		CivilianNpc npc = kit.civilian(10, true); // caller stands east (+X) of the criminal at the origin
		kit.witness("Car_Theft");

		behavior.onEnter(npc);

		ArgumentCaptor<Location> turned = ArgumentCaptor.forClass(Location.class);
		verify(npc.getEntity()).teleport(turned.capture());
		// Bukkit yaw 270 (= -90) faces +X: straight away from a criminal standing west of the caller
		double yaw = ((turned.getValue().getYaw() % 360.0) + 360.0) % 360.0;
		assertEquals(270.0, yaw, 1.0);
	}

	@Test
	@DisplayName("8 s of ticks land the call: the event is fired once with crime and location, state FLEEING")
	void callLandsAfterCallSeconds() {
		CivilianNpc npc = kit.civilian(10, true);
		kit.witness("Car_Theft");
		behavior.onEnter(npc);

		for (int i = 0; i < 20; i++) {
			kit.advanceSeconds(0.5);
			if (npc.getCurrentState() == CivilianState.CALLING) behavior.tick(npc);
		}

		assertEquals(1, kit.countEvents(CivilianCalledInEvent.class));
		CivilianCalledInEvent event = (CivilianCalledInEvent) kit.events.get(0);
		assertEquals("Car_Theft", event.getCrimeId());
		assertEquals(kit.at, event.getAt());
		assertEquals(CivilianState.FLEEING, npc.getCurrentState());
	}

	@Test
	@DisplayName("before Call_Seconds are up a tick fires nothing")
	void earlyTickFiresNothing() {
		CivilianNpc npc = kit.civilian(10, true);
		kit.witness("Car_Theft");
		behavior.onEnter(npc);

		kit.advanceSeconds(7);
		behavior.tick(npc);

		assertTrue(kit.events.isEmpty());
		assertEquals(CivilianState.CALLING, npc.getCurrentState());
	}

	@Test
	@DisplayName("a civilian in CALLING with no call attached gives up and goes IDLE")
	void tickWithoutCallGoesIdle() {
		CivilianNpc npc = kit.civilian(10, false);
		npc.transitionTo(CivilianState.CALLING);

		behavior.tick(npc);

		assertEquals(CivilianState.IDLE, npc.getCurrentState());
	}

	@Test
	@DisplayName("leaving CALLING clears the glow")
	void glowClearedOnExit() {
		CivilianNpc npc = kit.civilian(10, true);
		LivingEntity entity = npc.getEntity();

		behavior.onExit(npc);

		verify(entity).setGlowing(false);
	}

	@Test
	@DisplayName("a caller with no entity left exits cleanly")
	void exitWithoutEntityIsSafe() {
		CivilianNpc npc = kit.civilian(10, true);
		org.mockito.Mockito.when(npc.getEntity()).thenReturn(null);

		behavior.onExit(npc);

		verify(npc, never()).transitionTo(any());
	}

	@Test
	@DisplayName("leaving CALLING with the call still attached (alertFaction, an NPC attacker, player death) drops the call")
	void exitWithCallAttachedDropsCall() {
		CivilianNpc npc = kit.civilian(10, true);
		kit.witness("Car_Theft");
		behavior.onEnter(npc);
		int before = kit.transitions.size();

		behavior.onExit(npc);

		assertNull(npc.getWitnessCall());
		assertTrue(kit.service.activeCalls().isEmpty());
		assertEquals(before, kit.transitions.size());
		assertTrue(kit.debugContains("CALL_CANCEL Bob crime=Car_Theft reason=lost"), kit.debug::toString);
		verify(npc.getEntity()).setGlowing(false);
	}
}
