package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CopNpc loadout - Citizens replacing the entity (skin-fetch respawn) re-applies health, mark, equipment")
class CopNpcLoadoutTest {

	@Test
	@DisplayName("the loadout runs once on the spawn entity and again only when the NPC's entity is replaced")
	void loadout_reappliedOnlyOnReplacementEntity() {
		NPC          npc         = mock(NPC.class, RETURNS_DEEP_STUBS);
		LivingEntity first       = mock(LivingEntity.class);
		LivingEntity replacement = mock(LivingEntity.class);
		when(npc.isSpawned()).thenReturn(true);
		when(npc.getEntity()).thenReturn(first);

		CopNpc cop = new CopNpc(mock(JavaPlugin.class), npc, tier(), Map.of(), new Location(null, 0, 0, 0),
		                        mock(CopConfigProvider.class));

		List<LivingEntity> applied = new ArrayList<>();
		cop.setLoadout(applied::add);
		assertEquals(List.of(first), applied);

		cop.refreshLoadout();
		assertEquals(List.of(first), applied, "same entity: no re-apply");

		when(npc.getEntity()).thenReturn(replacement);
		cop.refreshLoadout();
		cop.refreshLoadout();
		assertEquals(List.of(first, replacement), applied, "replacement entity: re-applied exactly once");
	}

	private static CopTierConfig tier() {
		return new CopTierConfig(1, "&9Officer", 40.0, 2.0, 1.0, 2.0, false, false, List.of(), List.of(), null, null,
		                         null, null, NpcDifficulty.EASY, TacticsConfig.DEFAULT, 0.25);
	}
}
