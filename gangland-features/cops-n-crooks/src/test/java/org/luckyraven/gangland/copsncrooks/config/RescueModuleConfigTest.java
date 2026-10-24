package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.rescue.RescueService;
import org.luckyraven.gangland.copsncrooks.detainment.settings.RescueSettings;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pins the rescue wiring: a @Bean service that reads {@code DetainmentSettings.getRescue()} live. */
@DisplayName("RescueModuleConfig")
@SuppressWarnings("unchecked")
class RescueModuleConfigTest {

	@Test
	@DisplayName("rescueService is a @Bean and reads Detainment.Rescue from the settings on every click")
	void rescueService_readsTheSettingsLive() {
		DetainmentSettings settings = mock(DetainmentSettings.class);
		when(settings.getRescue()).thenReturn(new RescueSettings(false, 3, 1));
		CopManager copManager = mock(CopManager.class);

		RescueService service = new RescueModuleConfig(mock(JavaPlugin.class), new DependencyContainer()).rescueService(
				settings, copManager, mock(DetainmentService.class), new CuffLockRegistry(),
				mock(ReleasePipeline.class), mock(GangMembership.class), mock(WantedStars.class),
				mock(UserManager.class), mock(WantedMessages.class));

		assertNotNull(service);
		org.mockito.Mockito.verify(settings, org.mockito.Mockito.never()).getRescue();
		service.onClick(mock(Player.class), mock(Entity.class));
		org.mockito.Mockito.verify(settings, org.mockito.Mockito.atLeastOnce()).getRescue();
		verifyNoInteractions(copManager);
	}
}
