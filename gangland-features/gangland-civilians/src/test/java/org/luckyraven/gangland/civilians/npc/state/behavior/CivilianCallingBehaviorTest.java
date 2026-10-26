package org.luckyraven.gangland.civilians.npc.state.behavior;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessRules;
import org.luckyraven.gangland.civilians.witness.WitnessTestKit;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("CivilianCallingBehavior - the caller looks, takes out the phone, turns away and glows while 911 rings")
class CivilianCallingBehaviorTest {

	private final WitnessTestKit          kit      = new WitnessTestKit();
	private final CivilianCallingBehavior behavior = new CivilianCallingBehavior();

	/** XMaterial (Phone_Item): its enum init parses "MC: 1.xx" out of Bukkit.getVersion() (GrappleAddonLoadTest). */
	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
		when(org.bukkit.Bukkit.getServer().getVersion()).thenReturn("git-Spigot-abcdef (MC: 1.16.5)");
	}

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
	@DisplayName("Notice: entering turns the caller to look AT the criminal (0.21.1; was: away at once)")
	void enterLooksAtCriminal() {
		CivilianNpc npc = kit.civilian(10, true); // caller stands east (+X) of the criminal at the origin
		kit.witness("Car_Theft");

		behavior.onEnter(npc);

		ArgumentCaptor<Location> turned = ArgumentCaptor.forClass(Location.class);
		verify(npc.getEntity()).teleport(turned.capture());
		// Bukkit yaw 90 faces -X: straight at a criminal standing west of the caller
		assertEquals(90.0, yawOf(turned.getValue()), 1.0);
	}

	@Test
	@DisplayName("Dialling: after Notice_Seconds a tick turns the caller away from the criminal, once")
	void turnsAwayAfterNoticeSeconds() {
		CivilianNpc npc = kit.civilian(10, true);
		kit.witness("Car_Theft");
		behavior.onEnter(npc);

		kit.advanceSeconds(0.5);
		behavior.tick(npc);
		verify(npc.getEntity(), times(1)).teleport(any(Location.class));

		kit.advanceSeconds(0.5);
		behavior.tick(npc);
		kit.advanceSeconds(1);
		behavior.tick(npc);

		ArgumentCaptor<Location> turned = ArgumentCaptor.forClass(Location.class);
		verify(npc.getEntity(), times(2)).teleport(turned.capture());
		// Bukkit yaw 270 faces +X: straight away from a criminal standing west of the caller
		assertEquals(270.0, yawOf(turned.getAllValues().get(1)), 1.0);
	}

	@Test
	@DisplayName("Notice: a Notice_Particle puffs above the caller's head")
	void enterSpawnsNoticeParticle() {
		CivilianNpc npc = kit.civilian(10, true);
		kit.witness("Car_Theft");

		behavior.onEnter(npc);

		verify(kit.world).spawnParticle(any(org.bukkit.Particle.class), any(Location.class), anyInt(), anyDouble(),
		                                anyDouble(), anyDouble(), anyDouble());
	}

	@Test
	@DisplayName("Dialling: the caller holds Phone_Item, and gets its own main hand back on exit")
	void holdsPhoneThenRestoresHand() {
		CivilianNpc     npc       = kit.civilian(10, true);
		EntityEquipment equipment = mock(EntityEquipment.class);
		ItemStack       own       = mock(ItemStack.class);
		when(npc.getEntity().getEquipment()).thenReturn(equipment);
		when(equipment.getItemInMainHand()).thenReturn(own);
		kit.witness("Car_Theft");

		behavior.onEnter(npc);

		ArgumentCaptor<ItemStack> held = ArgumentCaptor.forClass(ItemStack.class);
		verify(equipment).setItemInMainHand(held.capture());
		assertEquals(org.bukkit.Material.CLOCK, held.getValue().getType());

		behavior.onExit(npc); // the exit through dropCall (call still attached) restores the hand too

		verify(equipment).setItemInMainHand(own);
	}

	@Test
	@DisplayName("Dialling: the hand comes back when the call lands (detached before the FLEEING transition)")
	void landedCallRestoresHand() {
		CivilianNpc     npc       = kit.civilian(10, true);
		EntityEquipment equipment = mock(EntityEquipment.class);
		ItemStack       own       = mock(ItemStack.class);
		when(npc.getEntity().getEquipment()).thenReturn(equipment);
		when(equipment.getItemInMainHand()).thenReturn(own);
		kit.witness("Car_Theft");
		behavior.onEnter(npc);

		kit.advanceSeconds(8);
		behavior.tick(npc);
		behavior.onExit(npc); // what CivilianNpc.transitionTo(FLEEING) does

		verify(equipment).setItemInMainHand(own);
	}

	@Test
	@DisplayName("Phone_Item \"\" leaves the hand alone")
	void emptyPhoneItemKeepsHand() {
		WitnessRules d = WitnessRules.DEFAULT;
		kit.rules = new WitnessRules(d.enabled(), d.range(), d.callSeconds(), d.callerGlow(), d.scareRange(),
		                             d.scareAngle(), d.reportAssault(), d.assaultRepeatSeconds(), d.viewAngle(),
		                             d.noticeSeconds(), "", "", d.intimidateRange(), d.scaredCooldownSeconds(),
		                             d.loudCrimes(), d.loudShotSeconds(), d.loudWeaponTypes());
		CivilianNpc     npc       = kit.civilian(10, true);
		EntityEquipment equipment = mock(EntityEquipment.class);
		when(npc.getEntity().getEquipment()).thenReturn(equipment);
		kit.witness("Car_Theft");

		behavior.onEnter(npc);
		behavior.onExit(npc);

		verify(equipment, never()).setItemInMainHand(any());
		verify(kit.world, never()).spawnParticle(any(org.bukkit.Particle.class), any(Location.class), anyInt(),
		                                         anyDouble(), anyDouble(), anyDouble(), anyDouble());
	}

	private static double yawOf(Location location) {
		return ((location.getYaw() % 360.0) + 360.0) % 360.0;
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
