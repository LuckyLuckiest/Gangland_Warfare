package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.EvasionClock;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseHabit;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLevelStat;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.ArgumentCaptor;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the evasion wiring: once every bean exists the module hands its clock to the core as the decay policy and to the
 * cop AI as a per-tick hook. Without either, line of sight never drops a star.
 */
@DisplayName("EvasionModuleConfig")
class EvasionModuleConfigTest {

	@Test
	@DisplayName("installEvasion installs the clock as the decay policy and as an AI-tick hook that ticks it")
	@SuppressWarnings("unchecked")
	void installEvasion_installsTheDecayPolicyAndTheAiTickHook() {
		EvasionClock clock   = mock(EvasionClock.class);
		WantedStars  stars   = mock(WantedStars.class);
		CopManager   manager = mock(CopManager.class);

		DependencyContainer container = new DependencyContainer();
		container.registerInstance(EvasionClock.class, clock);
		container.registerInstance(WantedStars.class, stars);
		container.registerInstance(CopManager.class, manager);

		new EvasionModuleConfig(mock(JavaPlugin.class), container).installEvasion();

		verify(stars).installDecayPolicy(clock);
		ArgumentCaptor<BiConsumer<Player, CopGroup>> hook = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addAiTickHook(hook.capture());

		Player   player = mock(Player.class);
		CopGroup group  = mock(CopGroup.class);
		hook.getValue().accept(player, group);
		verify(clock).tick(player, group);
	}

	@Test
	@DisplayName("the bean container runs it: installEvasion is a @PostConstruct")
	void installEvasion_isAPostConstruct() throws NoSuchMethodException {
		assertTrue(EvasionModuleConfig.class.getMethod("installEvasion").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("chaseLearner takes both chase repositories from the registry and hands them its caches")
	@SuppressWarnings("unchecked")
	void chaseLearner_wiresBothRepositories() {
		IRepository<ChaseHabit>     habits   = mock(IRepository.class);
		IRepository<ChaseLevelStat> levels   = mock(IRepository.class);
		RepositoryRegistry          registry = mock(RepositoryRegistry.class);
		when(registry.getRepository(ChaseHabit.class)).thenReturn(habits);
		when(registry.getRepository(ChaseLevelStat.class)).thenReturn(levels);

		new EvasionModuleConfig(mock(JavaPlugin.class), new DependencyContainer()).chaseLearner(
				mock(ChaseConfigLoader.class), registry);

		verify(habits).setDataSupplier(any());
		verify(levels).setDataSupplier(any());
	}
}
