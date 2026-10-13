package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PullOverSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PullOverTracker;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 0.20.0 lane patrol: {@link PatrolModuleConfig} builds the four beans and installs the resolver and the services. */
@DisplayName("PatrolModuleConfig - patrol wiring")
class PatrolWiringTest {

	private CopLoader         loader;
	private CopConfigProvider provider;
	private PatrolModuleConfig config;

	@BeforeEach
	void setUp() {
		provider = mock(CopConfigProvider.class);
		when(provider.getPatrolSettings()).thenReturn(org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings.DEFAULT);
		when(provider.getWarningSettings()).thenReturn(new WarningSettings(false, 12.0, 5, 30));
		when(provider.getPullOverSettings()).thenReturn(new PullOverSettings(false, 24.0, 12.0, 6, 1.0, 2, 150.0, 120));
		loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(provider);
		config = new PatrolModuleConfig(mock(JavaPlugin.class), new DependencyContainer());
	}

	@Test
	@DisplayName("the four patrol beans are @Bean methods and installPatrol is a @PostConstruct")
	void annotations() throws NoSuchMethodException {
		for (String name : new String[] {"brandishWarnings", "pullOverTracker", "patrolServices", "patrolManager"}) {
			boolean found = false;
			for (var method : PatrolModuleConfig.class.getDeclaredMethods())
				if (method.getName().equals(name) && method.isAnnotationPresent(Bean.class)) found = true;
			assertTrue(found, name + " must be a @Bean");
		}
		assertTrue(PatrolModuleConfig.class.getMethod("installPatrol").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("brandishWarnings reads Cops.Warning from the loaded provider on every call")
	void brandishWarnings_readsProvider() {
		BrandishWarnings warnings = config.brandishWarnings(loader, mock(CrimeService.class),
		                                                    mock(WantedMessages.class));

		assertNotNull(warnings);
		assertFalse(warnings.canWarn(mock(Player.class)));
		when(provider.getWarningSettings()).thenReturn(WarningSettings.DEFAULT);
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		assertTrue(warnings.canWarn(player));
	}

	@Test
	@DisplayName("pullOverTracker reads Cops.Pull_Over from the loaded provider")
	void pullOverTracker_readsProvider() {
		PullOverTracker tracker = config.pullOverTracker(loader, mock(CrimeService.class),
		                                                 mock(DetainmentEconomyContract.class),
		                                                 mock(WantedMessages.class));

		assertNotNull(tracker);
		assertFalse(tracker.canOrder(UUID.randomUUID()));
	}

	@Test
	@DisplayName("patrolServices carries the shared perception, radio and warnings and delegates wanted and group lookups")
	void patrolServices_delegates() {
		CopPerception    perception = new CopPerception();
		CopRadio         radio      = mock(CopRadio.class);
		TargetingManager targeting  = mock(TargetingManager.class);
		CopManager       manager    = mock(CopManager.class);
		BrandishWarnings warnings   = mock(BrandishWarnings.class);
		UUID             id         = UUID.randomUUID();
		CopGroup         group      = mock(CopGroup.class);
		Player           player     = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(targeting.isWanted(id)).thenReturn(true);
		when(manager.groupOf(id)).thenReturn(group);

		PatrolServices services = config.patrolServices(loader, perception, targeting, manager, warnings, radio);

		assertSame(perception, services.perception());
		assertSame(radio, services.radio());
		assertSame(warnings, services.warnings());
		assertTrue(services.wanted().test(player));
		assertSame(group, services.groupOf().apply(id));
		assertNotNull(services.patrol().get());
		assertNotNull(services.warning().get());
		assertNotNull(services.players());
	}

	@Test
	@DisplayName("patrolManager is built")
	void patrolManager_built() {
		PatrolManager manager = config.patrolManager(loader, mock(StationRegistry.class), mock(CopSpawnManager.class),
		                                             mock(NpcMarkManager.class), new CopPerception());

		assertNotNull(manager);
	}

	@Test
	@DisplayName("installPatrol hands the services to the spawn manager and registers the beat cops as a cop resolver")
	@SuppressWarnings("unchecked")
	void installPatrol_wiresSpawnManagerAndResolver() {
		CopSpawnManager spawn    = mock(CopSpawnManager.class);
		CopManager      copManager = mock(CopManager.class);
		PatrolManager   patrol   = mock(PatrolManager.class);
		PatrolServices  services = mock(PatrolServices.class);
		DependencyContainer container = new DependencyContainer();
		container.registerInstance(CopSpawnManager.class, spawn);
		container.registerInstance(CopManager.class, copManager);
		container.registerInstance(PatrolManager.class, patrol);
		container.registerInstance(PatrolServices.class, services);

		new PatrolModuleConfig(mock(JavaPlugin.class), container).installPatrol();

		verify(spawn).setPatrolServices(services);
		ArgumentCaptor<Function<Entity, CopNpc>> resolver = ArgumentCaptor.forClass(Function.class);
		verify(copManager).addCopResolver(resolver.capture());
		Entity entity = mock(Entity.class);
		CopNpc beat   = mock(CopNpc.class);
		when(patrol.beatCopOf(entity)).thenReturn(beat);
		assertSame(beat, resolver.getValue().apply(entity));
		verify(patrol).beatCopOf(any());
	}
}
