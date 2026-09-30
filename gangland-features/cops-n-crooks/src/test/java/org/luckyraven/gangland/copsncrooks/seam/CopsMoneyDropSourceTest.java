package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.item.money.MoneyDropContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B1 (phase H13): money drops classify a death at MONITOR, when the cop is already invalid. {@code isCopNpc} needs a
 * valid cop, so every cop fell through to the MOB tier; the dying-safe lookup is used instead.
 */
@DisplayName("CopsMoneyDropSource - dying bodies")
class CopsMoneyDropSourceTest {

	@Test
	@DisplayName("a dying (already invalid) cop body classifies as COP")
	void dyingCop_isCop() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());
		when(manager.isCopNpc(body)).thenReturn(false); // invalid during the death event
		when(manager.findDyingCop(body)).thenReturn(mock(CopNpc.class));

		assertEquals(MoneyDropContext.COP,
		             new CopsMoneyDropSource(manager, mock(CivilianNpcRegistry.class)).classify(body));
	}

	@Test
	@DisplayName("an entity that is neither a cop nor a civilian is not claimed")
	void unknown_null() {
		LivingEntity body = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());

		assertNull(new CopsMoneyDropSource(mock(CopManager.class), mock(CivilianNpcRegistry.class)).classify(body));
	}
}
