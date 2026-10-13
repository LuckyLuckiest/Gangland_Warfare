package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.StandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.Mockito;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.standoff.StandoffController;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	private CopGroup stuckGroup(UUID id) {
		CopGroup     group = mock(CopGroup.class);
		List<CopNpc> cops  = new ArrayList<>();
		for (int n = 0; n < 3; n++) {
			CopNpc cop = mock(CopNpc.class);
			when(cop.getCurrentState()).thenReturn(CopState.PURSUING);
			when(cop.getTargetPlayerId()).thenReturn(id);
			when(cop.isValid()).thenReturn(true);
			when(cop.millisUnreachable()).thenReturn(20_000L);
			cops.add(cop);
		}
		when(group.getCops()).thenReturn(cops);
		when(group.getSquad()).thenReturn(mock(NpcSquad.class));
		when(group.getTargetPlayerId()).thenReturn(id);
		return group;
	}

	@Test
	@DisplayName("the bean runs on the radio clock and the loaded cop config, and a handcuffed suspect never starts one")
	@SuppressWarnings("unchecked")
	void standoffController_wiresRadioClockAndDetainment() {
		CopLoader         loader   = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		CopRadio          radio    = mock(CopRadio.class);
		DetainmentService cuffs    = mock(DetainmentService.class);
		Player            player   = mock(Player.class);
		UUID              id       = UUID.randomUUID();
		when(loader.getLoadedProvider()).thenReturn(provider);
		when(provider.getStandoffSettings()).thenReturn(StandoffSettings.DEFAULT);
		when(radio.now()).thenReturn(5_000L);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getLocation()).thenReturn(new org.bukkit.Location(null, 0, 64, 0));
		StandoffModuleConfig config = new StandoffModuleConfig(mock(JavaPlugin.class), new DependencyContainer());
		CopGroup             group  = stuckGroup(id);

		config.standoffController(loader, radio, mock(PerimeterController.class), mock(WantedStars.class),
		                          mock(UserManager.class), cuffs).tick(player, group);
		verify(group).setStandoffUntil(5_000L + 45_000L);

		when(cuffs.isHandcuffed(player)).thenReturn(true);
		CopGroup other = stuckGroup(id);
		config.standoffController(loader, radio, mock(PerimeterController.class), mock(WantedStars.class),
		                          mock(UserManager.class), cuffs).tick(player, other);
		verify(other, Mockito.never()).setStandoffUntil(Mockito.anyLong());
	}
}
