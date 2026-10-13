package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.settings.SurrenderSettings;
import org.luckyraven.gangland.copsncrooks.detainment.surrender.SurrenderService;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.21.0 Surrender wiring: the bean reads {@code Detainment.Surrender} live, and the module registers the AI-tick and
 * cop-attacked hooks on the cop manager.
 */
@DisplayName("SurrenderModuleConfig")
class SurrenderModuleConfigTest {

	private CopManager            manager;
	private SurrenderService      service;
	private SurrenderModuleConfig config;

	@BeforeEach
	void setUp() {
		manager = mock(CopManager.class);
		service = mock(SurrenderService.class);

		DependencyContainer container = new DependencyContainer();
		container.registerInstance(CopManager.class, manager);
		container.registerInstance(SurrenderService.class, service);

		config = new SurrenderModuleConfig(mock(JavaPlugin.class), container);
	}

	@Test
	@DisplayName("the bean is built from the detainment settings and the cop collaborators")
	void bean_isBuilt() {
		DetainmentSettings settings = mock(DetainmentSettings.class);
		when(settings.getSurrender()).thenReturn(new SurrenderSettings(true, 3, 0.5, 0.5, 0.75, 10));

		SurrenderService built = config.surrenderService(settings, mock(CopRadio.class), mock(WantedMessages.class),
		                                                 mock(CrimeService.class), mock(DetainmentService.class));

		assertNotNull(built);
	}

	@Test
	@DisplayName("the service method is a @Bean and the hook registration a @PostConstruct")
	void annotations() throws NoSuchMethodException {
		assertTrue(SurrenderModuleConfig.class.getMethod("surrenderService", DetainmentSettings.class, CopRadio.class,
		                                                WantedMessages.class, CrimeService.class,
		                                                DetainmentService.class).isAnnotationPresent(Bean.class));
		assertTrue(SurrenderModuleConfig.class.getMethod("registerHooks").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("registerHooks wires the tick hook to service.tick")
	@SuppressWarnings("unchecked")
	void registersTickHook() {
		config.registerHooks();

		ArgumentCaptor<BiConsumer<Player, CopGroup>> captor = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addAiTickHook(captor.capture());
		Player   player = mock(Player.class);
		CopGroup group  = new CopGroup(UUID.randomUUID());
		captor.getValue().accept(player, group);

		verify(service).tick(player, group);
	}

	@Test
	@DisplayName("registerHooks wires the attacked hook to service.onCopAttacked")
	@SuppressWarnings("unchecked")
	void registersAttackedHook() {
		config.registerHooks();

		ArgumentCaptor<BiConsumer<CopNpc, Player>> captor = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addCopAttackedHook(captor.capture());
		CopNpc cop    = mock(CopNpc.class);
		Player player = mock(Player.class);
		captor.getValue().accept(cop, player);

		verify(service).onCopAttacked(cop, player);
	}
}
