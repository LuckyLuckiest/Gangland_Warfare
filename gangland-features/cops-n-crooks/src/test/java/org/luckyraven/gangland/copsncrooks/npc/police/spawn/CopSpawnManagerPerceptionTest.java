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

import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mockConstruction;
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

	@Test
	@DisplayName("setPerception hands the very instance to the next CopBehaviorFactory (not just any rebuild)")
	void setPerception_passesTheInstanceToTheFactory() {
		CopConfigProvider config = mock(CopConfigProvider.class);
		CopLoader         loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(config);
		@SuppressWarnings("unchecked") IRepository<CopSpawner> repository = mock(IRepository.class);
		CopSpawnManager manager = new CopSpawnManager(mock(JavaPlugin.class), loader, mock(NpcMarkManager.class),
		                                              mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class),
		                                              repository, mock(DetainmentService.class),
		                                              mock(CuffLockRegistry.class));
		CopPerception perception = new CopPerception();

		List<List<?>> args = new ArrayList<>();
		try (MockedConstruction<CopBehaviorFactory> built = mockConstruction(CopBehaviorFactory.class,
		                                                                       (mock, ctx) -> args.add(ctx.arguments()))) {
			manager.setPerception(perception);

			assertSame(perception, lastPerception(args));
		}
	}

	@Test
	@DisplayName("a reload (onInitialize) keeps the perception the lanes registered on")
	void reloadKeepsThePerception() {
		CopConfigProvider config = mock(CopConfigProvider.class);
		CopLoader         loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(config);
		@SuppressWarnings("unchecked") IRepository<CopSpawner> repository = mock(IRepository.class);
		CopSpawnManager manager = new CopSpawnManager(mock(JavaPlugin.class), loader, mock(NpcMarkManager.class),
		                                              mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class),
		                                              repository, mock(DetainmentService.class),
		                                              mock(CuffLockRegistry.class));
		CopPerception perception = new CopPerception();

		List<List<?>> args = new ArrayList<>();
		try (MockedConstruction<CopBehaviorFactory> built = mockConstruction(CopBehaviorFactory.class,
		                                                                       (mock, ctx) -> args.add(ctx.arguments()))) {
			manager.setPerception(perception);
			int afterSet = built.constructed().size();
			manager.onInitialize(false);

			assertSame(perception, lastPerception(args));
			org.junit.jupiter.api.Assertions.assertTrue(built.constructed().size() > afterSet,
			                                            "onInitialize rebuilt no factory");
		}
	}

	private static Object lastPerception(List<List<?>> args) {
		for (Object arg : args.get(args.size() - 1))
			if (arg instanceof CopPerception) return arg;
		return null;
	}
}
