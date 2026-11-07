package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.civilians.npc.npc.CitizensNpcs;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("CopNpcFactory.createCop - a missing tier config")
class CopNpcFactoryMissingTierTest {

	@Test
	@DisplayName("T-920: no tier config means no spawn, and no Citizens NPC is registered for it")
	void createCop_missingTierConfig_registersNoNpc() {
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getTierConfig(1)).thenReturn(null);
		CopNpcFactory factory = new CopNpcFactory(mock(JavaPlugin.class), provider, mock(CopBehaviorFactory.class),
		                                          mock(NpcMarkManager.class), mock(BartizanNpcWeapons.class),
		                                          mock(DownedTargetFilter.class));

		try (MockedStatic<NpcSupport> support = mockStatic(NpcSupport.class);
		     MockedStatic<CitizensNpcs> npcs = mockStatic(CitizensNpcs.class)) {
			support.when(NpcSupport::available).thenReturn(true);
			npcs.when(() -> CitizensNpcs.create(any(EntityType.class), anyString()))
			    .thenReturn(mock(NPC.class, RETURNS_DEEP_STUBS));

			CopNpc[] created = new CopNpc[1];
			assertDoesNotThrow(() -> created[0] = factory.createCop(new Location(null, 0, 0, 0), 1, false, null));

			assertNull(created[0]);
			npcs.verify(() -> CitizensNpcs.create(any(EntityType.class), anyString()), never());
		}
	}

}
