package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.standoff.StandoffController;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins the standoff wiring: the controller is a bean built from the radio, perimeter, stars, users and detainment, and
 * once it exists the module hands it to the cop AI as one tick hook and one cop-attacked hook. No scheduler task.
 */
@DisplayName("StandoffModuleConfig")
class StandoffModuleConfigTest {

	@Test
	@DisplayName("standoffController is a @Bean and builds from its collaborators")
	@SuppressWarnings("unchecked")
	void standoffController_isABeanBuiltFromMocks() throws NoSuchMethodException {
		StandoffModuleConfig config = new StandoffModuleConfig(mock(JavaPlugin.class), new DependencyContainer());

		StandoffController built = config.standoffController(mock(CopLoader.class), mock(CopRadio.class),
		                                                     mock(PerimeterController.class), mock(WantedStars.class),
		                                                     mock(UserManager.class), mock(DetainmentService.class));

		assertNotNull(built);
		assertTrue(StandoffModuleConfig.class.getMethod("standoffController", CopLoader.class, CopRadio.class,
		                                                PerimeterController.class, WantedStars.class,
		                                                UserManager.class, DetainmentService.class)
		                                     .isAnnotationPresent(Bean.class));
	}

	@Test
	@DisplayName("installStandoff is a @PostConstruct")
	void installStandoff_isAPostConstruct() throws NoSuchMethodException {
		assertTrue(StandoffModuleConfig.class.getMethod("installStandoff").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("installStandoff registers the controller tick and cop-attacked hooks on the CopManager")
	@SuppressWarnings("unchecked")
	void installStandoff_registersBothHooks() {
		StandoffController  controller = mock(StandoffController.class);
		CopManager          manager    = mock(CopManager.class);
		DependencyContainer container  = new DependencyContainer();
		container.registerInstance(StandoffController.class, controller);
		container.registerInstance(CopManager.class, manager);

		new StandoffModuleConfig(mock(JavaPlugin.class), container).installStandoff();

		ArgumentCaptor<BiConsumer<Player, CopGroup>> tick = ArgumentCaptor.forClass(BiConsumer.class);
		ArgumentCaptor<BiConsumer<CopNpc, Player>>   hit  = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addAiTickHook(tick.capture());
		verify(manager).addCopAttackedHook(hit.capture());

		Player   player = mock(Player.class);
		CopGroup group  = mock(CopGroup.class);
		CopNpc   cop    = mock(CopNpc.class);
		tick.getValue().accept(player, group);
		hit.getValue().accept(cop, player);
		verify(controller).tick(player, group);
		verify(controller).onCopAttacked(cop, player);
	}
}
