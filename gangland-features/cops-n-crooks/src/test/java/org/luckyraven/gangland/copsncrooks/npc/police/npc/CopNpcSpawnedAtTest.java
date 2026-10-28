package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/** 0.21.2 R3 (owner ruling T-601): a cop knows when he was spawned, on the wall clock CopCrimeReports reads. */
@DisplayName("CopNpc - spawn time")
class CopNpcSpawnedAtTest {

	@Test
	@DisplayName("a new cop's spawnedAt is the wall-clock time he was made")
	void newCop_spawnedAtIsNow() {
		long   before = System.currentTimeMillis();
		CopNpc cop    = new CopNpc(mock(JavaPlugin.class), mock(NPC.class, RETURNS_DEEP_STUBS), tier(), Map.of(),
		                           new Location(mock(World.class), 0, 64, 0), mock(CopConfigProvider.class));
		long   after  = System.currentTimeMillis();

		assertTrue(cop.getSpawnedAt() >= before && cop.getSpawnedAt() <= after, "spawnedAt " + cop.getSpawnedAt());
	}

	private static CopTierConfig tier() {
		return new CopTierConfig(1, "&9Officer", 40.0, 2.0, 1.2, 2.0, false, false, List.of(), List.of(), null, null,
		                         null, null, NpcDifficulty.EASY, TacticsConfig.DEFAULT, 0.25);
	}
}
