package org.luckyraven.gangland.copsncrooks.npc.police.state;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.behavior.PatrolBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.behavior.WarningBehavior;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 0.20.0 lane patrol: the factory hands the beat cops' services to the PATROL and WARNING behaviours. */
@DisplayName("CopBehaviorFactory - patrol services reach PATROL and WARNING")
class CopBehaviorFactoryPatrolTest {

	private BukkitStatics     bukkit;
	private CopConfigProvider provider;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install();
		provider = mock(CopConfigProvider.class);
		when(provider.getAiTickRate()).thenReturn(10);
		when(provider.getCuffCooldownTicks()).thenReturn(40);
		when(provider.getAlertRange()).thenReturn(40.0);
		when(provider.getRetreatSettings()).thenReturn(RetreatSettings.DEFAULT);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("with services, PATROL and WARNING are built on that very instance")
	void servicesReachBothBehaviours() {
		PatrolServices services = mock(PatrolServices.class);
		CopBehaviorFactory factory = new CopBehaviorFactory(provider, () -> mock(CopSpawnManager.class),
		                                                    mock(DetainmentService.class), new CuffLockRegistry(),
		                                                    id -> false, new CopPerception(), services);

		Map<CopState, CopBehavior> behaviors = factory.createBehaviors();

		assertSame(services, assertInstanceOf(PatrolBehavior.class, behaviors.get(CopState.PATROL)).services());
		assertSame(services, assertInstanceOf(WarningBehavior.class, behaviors.get(CopState.WARNING)).services());
	}

	@Test
	@DisplayName("without services (the older constructors) PATROL and WARNING are inert")
	void noServices_inert() {
		CopBehaviorFactory factory = new CopBehaviorFactory(provider, () -> mock(CopSpawnManager.class),
		                                                    mock(DetainmentService.class), new CuffLockRegistry(),
		                                                    id -> false);

		Map<CopState, CopBehavior> behaviors = factory.createBehaviors();

		assertNull(assertInstanceOf(PatrolBehavior.class, behaviors.get(CopState.PATROL)).services());
		assertNull(assertInstanceOf(WarningBehavior.class, behaviors.get(CopState.WARNING)).services());
	}
}
