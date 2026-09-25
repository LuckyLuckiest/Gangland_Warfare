package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins gi=52 (P2): {@code JetpackService.deactivate}/{@code deactivateAll} hardcoded
 * {@code player.setAllowFlight(false)} with no regard for the flight the player already had before activation. A
 * creative player wearing a jetpack has native mayfly; {@code JetpackActivateListener.onJoin} and any chestplate
 * change route through {@code scheduleChestplateCheck} -> activate()/deactivate() regardless of gamemode, so
 * removing the jetpack stripped mayfly Bukkit only restores on a gamemode change.
 */
@DisplayName("JetpackService — deactivate restores the pre-activation allowFlight state (gi=52)")
class JetpackServiceCreativeFlightTest {

	private BukkitStatics bukkit;

	@AfterEach
	void tearDown() {
		if (bukkit != null) bukkit.close();
	}

	private JetpackService service() {
		bukkit = BukkitStatics.install();
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		return new JetpackService(mock(FuelService.class), mock(JavaPlugin.class), mock(GadgetPhysicsConfig.class),
		                          mock(JetpackAddon.class));
	}

	private Player creativePlayer(Jetpack jetpack) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(jetpack.getPermission())).thenReturn(true);
		when(player.isOnline()).thenReturn(true);
		// Creative mode already grants native mayfly before the jetpack is ever equipped.
		when(player.getAllowFlight()).thenReturn(true);
		return player;
	}

	@Test
	@DisplayName("deactivate restores allowFlight=true for a creative player instead of forcing it false")
	void deactivate_restoresPreviousAllowFlight_forCreativePlayer() {
		JetpackService service = service();
		Jetpack        jetpack = Jetpack.builder().jetpackId("jp").build();
		Player         player  = creativePlayer(jetpack);

		service.activate(player, jetpack);
		service.deactivate(player);

		verify(player, never()).setAllowFlight(false);
	}

	@Test
	@DisplayName("deactivateAll restores allowFlight=true for a creative player instead of forcing it false")
	void deactivateAll_restoresPreviousAllowFlight_forCreativePlayer() {
		JetpackService service = service();
		Jetpack        jetpack = Jetpack.builder().jetpackId("jp").build();
		Player         player  = creativePlayer(jetpack);

		service.activate(player, jetpack);
		service.deactivateAll();

		verify(player, never()).setAllowFlight(false);
	}

	@Test
	@DisplayName("a survival player (no prior allowFlight) is still grounded on deactivate")
	void deactivate_survivalPlayer_stillSetFalse() {
		JetpackService service = service();
		Jetpack        jetpack = Jetpack.builder().jetpackId("jp").build();
		Player         player  = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(jetpack.getPermission())).thenReturn(true);
		when(player.isOnline()).thenReturn(true);
		when(player.getAllowFlight()).thenReturn(false); // survival: no mayfly before activation

		service.activate(player, jetpack);
		service.deactivate(player);

		verify(player).setAllowFlight(false);
	}
}
