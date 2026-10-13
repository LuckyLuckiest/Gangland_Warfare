package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.report.BreakingNews;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The news wiring: one {@link BreakingNews} bean, whose time check rides the cop AI tick. */
@DisplayName("PursuitReportModuleConfig")
class PursuitReportModuleConfigTest {

	@Test
	@DisplayName("breakingNews is a @Bean and builds from the arcs, the config and the messages")
	void breakingNews_isABean() throws NoSuchMethodException {
		PursuitReportModuleConfig config = new PursuitReportModuleConfig(mock(JavaPlugin.class), new DependencyContainer());

		assertNotNull(config.breakingNews(new ChaseArcs(System::currentTimeMillis), mock(ChaseConfigLoader.class),
		                                  mock(WantedMessages.class)));
		assertTrue(PursuitReportModuleConfig.class.getMethod("breakingNews", ChaseArcs.class, ChaseConfigLoader.class,
		                                                     WantedMessages.class).isAnnotationPresent(Bean.class));
	}

	@Test
	@DisplayName("installNews is a @PostConstruct that registers the news time check as a cop AI-tick hook")
	@SuppressWarnings("unchecked")
	void installNews_registersTheAiTickHook() throws NoSuchMethodException {
		BreakingNews news    = mock(BreakingNews.class);
		CopManager   manager = mock(CopManager.class);
		DependencyContainer container = new DependencyContainer();
		container.registerInstance(BreakingNews.class, news);
		container.registerInstance(CopManager.class, manager);

		new PursuitReportModuleConfig(mock(JavaPlugin.class), container).installNews();

		ArgumentCaptor<BiConsumer<Player, CopGroup>> hook = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addAiTickHook(hook.capture());
		Player   player = mock(Player.class);
		CopGroup group  = mock(CopGroup.class);
		hook.getValue().accept(player, group);
		verify(news).onAiTick(player, group);
		assertTrue(PursuitReportModuleConfig.class.getMethod("installNews").isAnnotationPresent(PostConstruct.class));
	}
}
