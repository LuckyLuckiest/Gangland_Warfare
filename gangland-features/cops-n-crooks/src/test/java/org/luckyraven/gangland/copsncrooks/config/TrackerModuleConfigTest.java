package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.TrackerTags;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link TrackerModuleConfig}: the tag book bean and its install into the cop AI tick. */
@DisplayName("TrackerModuleConfig")
class TrackerModuleConfigTest {

	@Test
	@DisplayName("trackerTags builds from the loader, the user manager, the radio and the plugin")
	@SuppressWarnings("unchecked")
	void trackerTags_isBuilt() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getName()).thenReturn("Gangland_Warfare");
		TrackerModuleConfig config = new TrackerModuleConfig(plugin, new DependencyContainer());

		assertNotNull(config.trackerTags(mock(CopLoader.class), mock(UserManager.class), mock(CopRadio.class),
		                                 mock(WantedMessages.class), plugin));
	}

	@Test
	@DisplayName("installTracker registers exactly one AI tick hook, and it feeds TrackerTags.tick")
	@SuppressWarnings("unchecked")
	void installTracker_registersTickHook() {
		DependencyContainer container = new DependencyContainer();
		TrackerTags         tags      = mock(TrackerTags.class);
		CopManager          manager   = mock(CopManager.class);
		container.registerInstance(TrackerTags.class, tags);
		container.registerInstance(CopManager.class, manager);

		new TrackerModuleConfig(mock(JavaPlugin.class), container).installTracker();

		ArgumentCaptor<BiConsumer<Player, CopGroup>> hook = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager, times(1)).addAiTickHook(hook.capture());
		Player   player = mock(Player.class);
		CopGroup group  = mock(CopGroup.class);
		hook.getValue().accept(player, group);
		verify(tags).tick(player, group);
	}

	@Test
	@DisplayName("the bean container runs it: installTracker is a @PostConstruct")
	void installTracker_isAPostConstruct() throws NoSuchMethodException {
		assertTrue(TrackerModuleConfig.class.getMethod("installTracker").isAnnotationPresent(PostConstruct.class));
	}
}
