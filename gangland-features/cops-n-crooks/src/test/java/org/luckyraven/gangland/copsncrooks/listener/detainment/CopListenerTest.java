package org.luckyraven.gangland.copsncrooks.listener.detainment;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.mockito.InOrder;

import java.util.ArrayList;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Review fix round 1 (spec 4.9): a killed cop must leave its group's squad immediately rather than waiting for the
 * next AI sweep, so {@link CopListener#onCopDeath} calls {@link CopNpc#leaveSquad()} before {@link CopNpc#destroy()}.
 */
@DisplayName("CopListener - cop death")
class CopListenerTest {

	@Test
	@DisplayName("onCopDeath leaves the squad before destroying the cop")
	void onCopDeath_leavesSquadBeforeDestroy() {
		CopManager  copManager = mock(CopManager.class);
		CopListener listener   = new CopListener(copManager);

		LivingEntity entity = mock(LivingEntity.class);
		CopNpc       cop    = mock(CopNpc.class);
		when(copManager.isCopNpc(entity)).thenReturn(true);
		when(copManager.findCopByEntity(entity)).thenReturn(cop);

		EntityDeathEvent event = mock(EntityDeathEvent.class);
		when(event.getEntity()).thenReturn(entity);
		when(event.getDrops()).thenReturn(new ArrayList<>());

		listener.onCopDeath(event);

		InOrder inOrder = inOrder(cop);
		inOrder.verify(cop).leaveSquad();
		inOrder.verify(cop).destroy();
	}
}
