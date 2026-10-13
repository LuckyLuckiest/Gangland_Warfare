package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/** 0.20.0 lane patrol: the spawn manager takes the beat cops' services and rebuilds its behaviour factories with them. */
@DisplayName("CopSpawnManager - patrol services seam")
class CopSpawnManagerPatrolTest {

	private CopSpawnManager manager() {
		CopConfigProvider config = mock(CopConfigProvider.class);
		CopLoader         loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(config);
		@SuppressWarnings("unchecked") IRepository<CopSpawner> repository = mock(IRepository.class);
		return new CopSpawnManager(mock(JavaPlugin.class), loader, mock(NpcMarkManager.class),
		                           mock(BartizanNpcWeapons.class), mock(DownedTargetFilter.class), repository,
		                           mock(DetainmentService.class), mock(CuffLockRegistry.class));
	}

	private static Object lastServices(List<List<?>> args) {
		for (Object arg : args.get(args.size() - 1))
			if (arg instanceof PatrolServices) return arg;
		return null;
	}

	@Test
	@DisplayName("setPatrolServices rebuilds the cop factory so the next spawned cop gets the patrol behaviours")
	void setPatrolServices_rebuildsTheFactory() {
		CopSpawnManager manager = manager();
		var before = manager.copNpcFactory;
		assertNotNull(before);

		manager.setPatrolServices(mock(PatrolServices.class));

		assertNotNull(manager.copNpcFactory);
		assertNotSame(before, manager.copNpcFactory);
	}

	@Test
	@DisplayName("setPatrolServices hands the very instance to the next CopBehaviorFactory")
	void setPatrolServices_passesTheInstanceToTheFactory() {
		CopSpawnManager manager  = manager();
		PatrolServices  services = mock(PatrolServices.class);

		List<List<?>> args = new ArrayList<>();
		try (MockedConstruction<CopBehaviorFactory> built = mockConstruction(CopBehaviorFactory.class,
		                                                                       (mock, ctx) -> args.add(ctx.arguments()))) {
			manager.setPatrolServices(services);

			assertSame(services, lastServices(args));
		}
	}

	@Test
	@DisplayName("a reload (onInitialize) keeps the services the patrol lane registered")
	void reloadKeepsTheServices() {
		CopSpawnManager manager  = manager();
		PatrolServices  services = mock(PatrolServices.class);

		List<List<?>> args = new ArrayList<>();
		try (MockedConstruction<CopBehaviorFactory> built = mockConstruction(CopBehaviorFactory.class,
		                                                                       (mock, ctx) -> args.add(ctx.arguments()))) {
			manager.setPatrolServices(services);
			int afterSet = built.constructed().size();
			manager.onInitialize(false);

			assertSame(services, lastServices(args));
			assertTrue(built.constructed().size() > afterSet, "onInitialize rebuilt no factory");
		}
	}
}
