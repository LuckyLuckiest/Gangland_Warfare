package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import com.cryptomorin.xseries.XAttribute;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.entity.EntityMark;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CopNpcFactory loadout - a replacement entity gets the tier health and POLICE mark, and keeps its damage")
class CopNpcFactoryLoadoutTest {

	@Test
	@DisplayName("replacement entity: tier max health, POLICE mark, current health carried over from the old entity")
	void replacementEntity_getsTierLoadoutAndKeepsCurrentHealth() {
		NPC            npc      = mock(NPC.class, RETURNS_DEEP_STUBS);
		NpcMarkManager marks    = mock(NpcMarkManager.class);
		LivingEntity   first    = living();
		LivingEntity   replaced = living();
		when(npc.isSpawned()).thenReturn(true);
		when(npc.getEntity()).thenReturn(first);
		CopTierConfig tier = tier();
		CopNpc cop = new CopNpc(mock(JavaPlugin.class), npc, tier, Map.of(), new Location(null, 0, 0, 0),
		                        mock(CopConfigProvider.class));

		cop.setLoadout(CopNpcFactory.loadout(cop, tier, marks, null));
		verify(marks).setMark(first, EntityMark.POLICE.name());
		verify(first).setHealth(40.0);

		when(first.getHealth()).thenReturn(13.0);
		when(npc.getEntity()).thenReturn(replaced);
		cop.refreshLoadout();

		verify(replaced.getAttribute(XAttribute.MAX_HEALTH.get())).setBaseValue(40.0);
		verify(marks).setMark(replaced, EntityMark.POLICE.name());
		InOrder order = inOrder(replaced);
		order.verify(replaced).setHealth(40.0);
		order.verify(replaced).setHealth(13.0);
	}

	private static LivingEntity living() {
		LivingEntity      entity    = mock(LivingEntity.class);
		AttributeInstance attribute = mock(AttributeInstance.class);
		when(attribute.getValue()).thenReturn(40.0);
		when(entity.getAttribute(XAttribute.MAX_HEALTH.get())).thenReturn(attribute);
		return entity;
	}

	private static CopTierConfig tier() {
		return new CopTierConfig(1, "&9Officer", 40.0, 2.0, 1.0, 2.0, false, false, List.of(), List.of(), null, null,
		                         null, null, NpcDifficulty.EASY, TacticsConfig.DEFAULT, 0.25);
	}
}
