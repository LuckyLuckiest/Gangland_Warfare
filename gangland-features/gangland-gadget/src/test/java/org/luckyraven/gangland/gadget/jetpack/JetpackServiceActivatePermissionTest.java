package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.item.fuel.FuelService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins WS7 G4 part 2, review I3 shape: {@link JetpackService#activate} enforces the jetpack's per-item permission
 * ({@code Jetpack#getPermission()}, mirroring Car's {@code CarInteractListener} check) before a session is ever
 * created — but does so <b>silently</b>. This method is reached by every {@code scheduleChestplateCheck} caller,
 * including join ({@code JetpackActivateListener}) and dismount/undown ({@code JetpackSessionLifecycleListener}),
 * not just a deliberate equip — a message here would fire on every one of those. The denial message instead comes
 * from {@code JetpackEquipListener}'s two deliberate-interaction paths only (see
 * {@code JetpackEquipListenerPermissionTest}).
 */
@DisplayName("JetpackService.activate — per-item permission check is silent (WS7 G4 review I3)")
class JetpackServiceActivatePermissionTest {

	@Test
	@DisplayName("a player lacking the jetpack's permission is denied activation, silently")
	void activate_missingPermission_deniedSilently() {
		FuelService         fuelService   = mock(FuelService.class);
		JavaPlugin          plugin        = mock(JavaPlugin.class);
		GadgetPhysicsConfig physicsConfig = mock(GadgetPhysicsConfig.class);
		JetpackAddon        jetpackAddon  = mock(JetpackAddon.class);

		JetpackService service = new JetpackService(fuelService, plugin, physicsConfig, jetpackAddon);

		Jetpack jetpack = Jetpack.builder().jetpackId("locked").build();
		Player  player  = mock(Player.class);

		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(jetpack.getPermission())).thenReturn(false);

		service.activate(player, jetpack);

		assertFalse(service.isActive(player));
		verify(player, never()).sendMessage(anyString());
	}
}
