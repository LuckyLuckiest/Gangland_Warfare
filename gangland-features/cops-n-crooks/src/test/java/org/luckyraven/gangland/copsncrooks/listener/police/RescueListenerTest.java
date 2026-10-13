package org.luckyraven.gangland.copsncrooks.listener.police;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.rescue.RescueService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link RescueListener}: hands the clicker and the clicked entity to the service; Citizens-gated. */
@DisplayName("RescueListener")
class RescueListenerTest {

	private final RescueService      service  = mock(RescueService.class);
	private final RescueListener     listener = new RescueListener(service);
	private final Player             clicker  = mock(Player.class);
	private final NPC                npc      = mock(NPC.class);
	private final NPCRightClickEvent event    = mock(NPCRightClickEvent.class);

	@Test
	@DisplayName("a right-click delegates (clicker, entity) to the service")
	void delegatesClickerAndEntity() {
		Entity entity = mock(Entity.class);
		when(event.getClicker()).thenReturn(clicker);
		when(event.getNPC()).thenReturn(npc);
		when(npc.getEntity()).thenReturn(entity);

		listener.onNpcRightClick(event);

		verify(service).onClick(clicker, entity);
	}

	@Test
	@DisplayName("an NPC with no entity (despawned) is ignored")
	void nullEntity_ignored() {
		when(event.getClicker()).thenReturn(clicker);
		when(event.getNPC()).thenReturn(npc);
		when(npc.getEntity()).thenReturn(null);

		listener.onNpcRightClick(event);

		verify(service, never()).onClick(any(), any());
	}

	@Test
	@DisplayName("registered only when Citizens is present")
	void citizensGated() {
		assertEquals("isCitizensAvailable", RescueListener.class.getAnnotation(ListenerHandler.class).condition());
	}
}
