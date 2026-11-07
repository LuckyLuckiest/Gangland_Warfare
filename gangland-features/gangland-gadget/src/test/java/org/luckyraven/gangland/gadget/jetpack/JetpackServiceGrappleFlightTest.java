package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-950: a taut grapple rope lends allow-flight ({@code GrappleService.lendFlight}) and takes it back when it goes
 * slack. A jetpack that activates during the loan used to capture the lent {@code true} as the player's own
 * pre-activation flight, so {@code deactivate} restored it for good and the survival player could double-tap to fly.
 */
@DisplayName("JetpackService — a grapple-lent allowFlight is not the player's own (T-950)")
class JetpackServiceGrappleFlightTest {

	private BukkitStatics bukkit;

	@AfterEach
	void tearDown() {
		if (bukkit != null) bukkit.close();
	}

	private JetpackService service(GrappleService grappleService) {
		bukkit = BukkitStatics.install();
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		return new JetpackService(mock(FuelService.class), mock(JavaPlugin.class), mock(GadgetPhysicsConfig.class),
		                          mock(JetpackAddon.class), grappleService);
	}

	private Player survivalPlayerWithAllowFlight(Jetpack jetpack) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(jetpack.getPermission())).thenReturn(true);
		when(player.isOnline()).thenReturn(true);
		when(player.getAllowFlight()).thenReturn(true);
		return player;
	}

	@Test
	@DisplayName("allowFlight lent by a taut rope is not restored as true when the jetpack comes off")
	void deactivate_afterGrappleLentFlight_setsAllowFlightFalse() {
		GrappleService grappleService = mock(GrappleService.class);
		JetpackService service        = service(grappleService);
		Jetpack        jetpack        = Jetpack.builder().jetpackId("jp").build();
		Player         player         = survivalPlayerWithAllowFlight(jetpack);
		when(grappleService.holdsFlight(player)).thenReturn(true);

		service.activate(player, jetpack);
		service.deactivate(player);

		assertFalse(lastAllowFlight(player), "the rope's loan must not become the player's own flight");
	}

	@Test
	@DisplayName("a player's own allowFlight is still restored when no rope is lending")
	void deactivate_noGrappleLoan_stillRestoresOwnFlight() {
		JetpackService service = service(mock(GrappleService.class));
		Jetpack        jetpack = Jetpack.builder().jetpackId("jp").build();
		Player         player  = survivalPlayerWithAllowFlight(jetpack);

		service.activate(player, jetpack);
		service.deactivate(player);

		assertTrue(lastAllowFlight(player));
	}

	private static boolean lastAllowFlight(Player player) {
		ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
		verify(player, atLeastOnce()).setAllowFlight(captor.capture());
		return captor.getValue();
	}
}
