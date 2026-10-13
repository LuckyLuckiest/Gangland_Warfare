package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perch.OverwatchController;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link OverwatchModuleConfig}: one controller bean, and the controller's tick rides the cop AI tick. */
@DisplayName("OverwatchModuleConfig wiring")
class OverwatchWiringTest {

	@Test
	@DisplayName("exactly one @Bean method returns the OverwatchController")
	void oneControllerBean() {
		long producers = Arrays.stream(OverwatchModuleConfig.class.getDeclaredMethods())
		                       .filter(m -> m.isAnnotationPresent(Bean.class))
		                       .filter(m -> m.getReturnType() == OverwatchController.class).count();

		assertEquals(1L, producers);
	}

	@Test
	@DisplayName("installOverwatch registers an AI tick hook that calls controller.tick")
	@SuppressWarnings({"unchecked", "rawtypes"})
	void installRegistersTheTickHook() {
		DependencyContainer container  = mock(DependencyContainer.class);
		CopManager          manager    = mock(CopManager.class);
		OverwatchController controller = mock(OverwatchController.class);
		when(container.getInstance(CopManager.class)).thenReturn(manager);
		when(container.getInstance(OverwatchController.class)).thenReturn(controller);
		OverwatchModuleConfig config = new OverwatchModuleConfig(mock(JavaPlugin.class), container);

		config.installOverwatch();

		ArgumentCaptor<BiConsumer> hook = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addAiTickHook(hook.capture());
		Player   player = mock(Player.class);
		CopGroup group  = mock(CopGroup.class);
		hook.getValue().accept(player, group);
		verify(controller).tick(player, group);
	}
}
