package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.breach.BreachController;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link BreachModuleConfig}: the controller bean and the hooks that feed and stop it. */
@DisplayName("BreachModuleConfig")
class BreachModuleConfigTest {

	private final DependencyContainer container = mock(DependencyContainer.class);
	private final BreachModuleConfig  config    = new BreachModuleConfig(mock(JavaPlugin.class), container);

	@Test
	@DisplayName("breachController is a @Bean that builds a controller")
	void breachController_isABean() throws Exception {
		assertTrue(BreachModuleConfig.class.getMethod("breachController", CopLoader.class, CopRadio.class)
		                                   .isAnnotationPresent(Bean.class));

		assertNotNull(config.breachController(mock(CopLoader.class), mock(CopRadio.class)));
	}

	@Test
	@DisplayName("installBreach is a @PostConstruct: tick becomes an AI tick hook and endAll a shutdown hook")
	@SuppressWarnings("unchecked")
	void installBreach_hooksTickAndShutdown() throws Exception {
		assertTrue(BreachModuleConfig.class.getMethod("installBreach").isAnnotationPresent(PostConstruct.class));
		CopManager       manager    = mock(CopManager.class);
		BreachController controller = mock(BreachController.class);
		when(container.getInstance(CopManager.class)).thenReturn(manager);
		when(container.getInstance(BreachController.class)).thenReturn(controller);

		config.installBreach();

		ArgumentCaptor<BiConsumer<Player, CopGroup>> tick = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addAiTickHook(tick.capture());
		Player   player = mock(Player.class);
		CopGroup group  = mock(CopGroup.class);
		tick.getValue().accept(player, group);
		verify(controller).tick(player, group);

		ArgumentCaptor<Runnable> shutdown = ArgumentCaptor.forClass(Runnable.class);
		verify(manager).addShutdownHook(shutdown.capture());
		shutdown.getValue().run();
		verify(controller).endAll();
	}
}
