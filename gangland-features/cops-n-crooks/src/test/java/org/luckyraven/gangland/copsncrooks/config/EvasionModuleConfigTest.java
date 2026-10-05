package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.EvasionClock;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

}
