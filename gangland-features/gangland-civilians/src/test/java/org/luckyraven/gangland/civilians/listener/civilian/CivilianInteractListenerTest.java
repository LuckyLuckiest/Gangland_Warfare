package org.luckyraven.gangland.civilians.listener.civilian;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessTestKit;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CivilianInteractListener - the criminal right-clicks the caller to take the phone (0.21.1)")
class CivilianInteractListenerTest {

	private final WitnessTestKit           kit      = new WitnessTestKit();
	private final CivilianInteractListener listener = new CivilianInteractListener(kit.civilians, kit.service);
	private final NPC                      npc      = mock(NPC.class);
	private final NPCRightClickEvent       event    = mock(NPCRightClickEvent.class);

	private CivilianNpc clicked(double distance, Player clicker) {
		CivilianNpc                   civilian = kit.civilian(distance, true);
		org.bukkit.entity.LivingEntity entity  = civilian.getEntity();
		UUID                          id       = entity.getUniqueId();
		when(kit.civilians.getNpc(id)).thenReturn(civilian);
		when(npc.getEntity()).thenReturn(entity);
		when(event.getNPC()).thenReturn(npc);
		when(event.getClicker()).thenReturn(clicker);
		return civilian;
	}

	@Test
	@DisplayName("the criminal right-clicks his CALLING caller within Intimidate_Range: the call is stopped")
	void criminalRightClickIntimidates() {
		CivilianNpc caller = clicked(3, kit.criminal);
		kit.witness("Car_Theft");

		listener.onNpcRightClick(event);

		assertNull(caller.getWitnessCall());
		assertEquals(CivilianState.FLEEING, caller.getCurrentState());
	}

	@Test
	@DisplayName("another player right-clicking the caller changes nothing")
	void strangerIgnored() {
		Player stranger = mock(Player.class);
		when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
		when(stranger.getLocation()).thenReturn(kit.at.clone());
		CivilianNpc caller = clicked(3, stranger);
		kit.witness("Car_Theft");

		listener.onNpcRightClick(event);

		assertSame(caller, caller.getWitnessCall().caller());
		assertEquals(CivilianState.CALLING, caller.getCurrentState());
	}

	@Test
	@DisplayName("right-clicking a civilian who is not calling does nothing (open question 10: no)")
	void nonCallerIgnored() {
		CivilianNpc idle = clicked(3, kit.criminal);

		listener.onNpcRightClick(event);

		assertEquals(CivilianState.IDLE, idle.getCurrentState());
		assertEquals(0, kit.transitions.size());
	}

	@Test
	@DisplayName("a despawned NPC or a non-civilian NPC is ignored")
	void notACivilian() {
		when(event.getNPC()).thenReturn(npc);
		when(event.getClicker()).thenReturn(kit.criminal);
		when(npc.getEntity()).thenReturn(null);

		listener.onNpcRightClick(event);

		when(npc.getEntity()).thenReturn(mock(org.bukkit.entity.LivingEntity.class));
		listener.onNpcRightClick(event);

		assertEquals(0, kit.transitions.size());
	}

	@Test
	@DisplayName("registered only when Citizens is present")
	void citizensGated() {
		assertEquals("isCitizensAvailable", CivilianInteractListener.class.getAnnotation(ListenerHandler.class)
		                                                                 .condition());
	}
}
