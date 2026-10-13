package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpcFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.repository.IRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 0.20 seam: the spawn manager takes the shared {@link CopPerception} and rebuilds its behaviour factories with it. */
@DisplayName("CopSpawnManager - perception seam")
class CopSpawnManagerPerceptionTest {

	@Test
	@DisplayName("setPerception rebuilds the cop factory so the next spawned cop's behaviours use it")
	void setPerception_rebuildsTheFactory() {
		CopConfigProvider config = mock(CopConfigProvider.class);
		CopLoader         loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(config);
		@SuppressWarnings("unchecked") IRepository<CopSpawner> repository = mock(IRepository.class);
		CopSpawnManager manager = new CopSpawnManager(mock(JavaPlugin.class), loader, mock(NpcMarkManager.class),
		                                              mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class),
		                                              repository, mock(DetainmentService.class),
		                                              mock(CuffLockRegistry.class));
		CopNpcFactory before = manager.copNpcFactory;
		assertNotNull(before);

		manager.setPerception(new CopPerception());

		assertNotNull(manager.copNpcFactory);
		assertNotSame(before, manager.copNpcFactory);
	}
}
