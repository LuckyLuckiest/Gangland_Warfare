package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.config.CivilianDropConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianWearableConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H12 (GL-4): a squad casualty ({@link NpcSquad#memberDown}) is signalled before the civilian's drops are
 * resolved, so a squad-mate's Man_Down/Leader_Down line names the spot correctly rather than an already-cleared one.
 */
@DisplayName("CivilianDeathListener - squad casualty before drops")
class CivilianDeathListenerTest {

	@Test
	@DisplayName("a squad member's death calls memberDown before drops are resolved")
	void memberDownCalled_beforeDrops() {
		CivilianService civilianService = mock(CivilianService.class);
		CivilianNpc     npc             = mock(CivilianNpc.class);
		NpcSquad        squad           = mock(NpcSquad.class);
		UUID            npcId           = UUID.randomUUID();

		when(civilianService.getNpc(npcId)).thenReturn(npc);
		when(npc.getSquad()).thenReturn(squad);
		when(npc.getTypeConfig()).thenReturn(
				new CivilianTypeConfig("pedestrian", "Pedestrian", EntityType.PLAYER, 20.0, false,
				                       new CivilianWearableConfig("", "", "", ""), List.of(), List.of(), List.of(),
				                       new CivilianDropConfig(List.of(), 0.0), null, "pedestrian"));

		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getUniqueId()).thenReturn(npcId);

		EntityDeathEvent event = mock(EntityDeathEvent.class);
		when(event.getEntity()).thenReturn(entity);
		when(event.getDrops()).thenReturn(new ArrayList<>());

		CivilianDeathListener listener = new CivilianDeathListener(civilianService, null);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			PluginManager pluginManager = mock(PluginManager.class);
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

			listener.onCivilianDeath(event);
		}

		InOrder order = inOrder(squad, event, npc);
		order.verify(squad).memberDown(npc);
		order.verify(event).getDrops();
		order.verify(npc).markForRemoval();
	}

	@Test
	@DisplayName("a civilian with no squad dies without touching memberDown")
	void noSquad_neverCallsMemberDown() {
		CivilianService civilianService = mock(CivilianService.class);
		CivilianNpc     npc             = mock(CivilianNpc.class);
		UUID            npcId           = UUID.randomUUID();

		when(civilianService.getNpc(npcId)).thenReturn(npc);
		when(npc.getSquad()).thenReturn(null);
		when(npc.getTypeConfig()).thenReturn(
				new CivilianTypeConfig("pedestrian", "Pedestrian", EntityType.PLAYER, 20.0, false,
				                       new CivilianWearableConfig("", "", "", ""), List.of(), List.of(), List.of(),
				                       new CivilianDropConfig(List.of(), 0.0), null, "pedestrian"));

		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getUniqueId()).thenReturn(npcId);

		EntityDeathEvent event = mock(EntityDeathEvent.class);
		when(event.getEntity()).thenReturn(entity);
		when(event.getDrops()).thenReturn(new ArrayList<>());

		CivilianDeathListener listener = new CivilianDeathListener(civilianService, null);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			PluginManager pluginManager = mock(PluginManager.class);
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

			listener.onCivilianDeath(event);
		}

		verify(npc).markForRemoval();
	}
}
