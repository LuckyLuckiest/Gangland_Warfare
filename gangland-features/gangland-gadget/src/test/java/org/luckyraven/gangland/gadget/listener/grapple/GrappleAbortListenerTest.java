package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.grapple.GrappleService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins WS8 G2/G3 (+ fix round 1): {@link GrappleAbortListener} cancels an active pull on real damage, sneak-start,
 * teleport, world-change or death, forgets a player entirely on quit (F3), and is a no-op for an inactive player or
 * a sneak-release.
 */
@DisplayName("GrappleAbortListener — abort triggers (WS8 G2/G3)")
class GrappleAbortListenerTest {

	@Test
	@DisplayName("EntityDamageEvent on an active player cancels the pull")
	void onDamage_activePlayer_cancels() {
		GrappleService service  = mock(GrappleService.class);
		Player         player   = mock(Player.class);
		when(service.isActive(player)).thenReturn(true);

		EntityDamageEvent event = mock(EntityDamageEvent.class);
		when(event.getEntity()).thenReturn(player);

		new GrappleAbortListener(service).onDamage(event);

		verify(service).cancel(player);
	}

	@Test
	@DisplayName("EntityDamageEvent on an inactive player is a no-op")
	void onDamage_inactivePlayer_noop() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.isActive(player)).thenReturn(false);

		EntityDamageEvent event = mock(EntityDamageEvent.class);
		when(event.getEntity()).thenReturn(player);

		new GrappleAbortListener(service).onDamage(event);

		verify(service, never()).cancel(player);
	}

	@Test
	@DisplayName("PlayerToggleSneakEvent with isSneaking()==true on an active player cancels the pull")
	void onToggleSneak_sneakStart_activePlayer_cancels() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.isActive(player)).thenReturn(true);

		PlayerToggleSneakEvent event = mock(PlayerToggleSneakEvent.class);
		when(event.getPlayer()).thenReturn(player);
		when(event.isSneaking()).thenReturn(true);

		new GrappleAbortListener(service).onToggleSneak(event);

		verify(service).cancel(player);
	}

	@Test
	@DisplayName("PlayerToggleSneakEvent with isSneaking()==false (sneak-release) is a no-op")
	void onToggleSneak_sneakRelease_noop() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);

		PlayerToggleSneakEvent event = mock(PlayerToggleSneakEvent.class);
		when(event.getPlayer()).thenReturn(player);
		when(event.isSneaking()).thenReturn(false);

		new GrappleAbortListener(service).onToggleSneak(event);

		verify(service, never()).cancel(player);
	}

	@Test
	@DisplayName("PlayerTeleportEvent on an active player cancels the pull")
	void onTeleport_activePlayer_cancels() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.isActive(player)).thenReturn(true);

		PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
		when(event.getPlayer()).thenReturn(player);

		new GrappleAbortListener(service).onTeleport(event);

		verify(service).cancel(player);
	}

	@Test
	@DisplayName("PlayerChangedWorldEvent on an active player cancels the pull")
	void onChangeWorld_activePlayer_cancels() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.isActive(player)).thenReturn(true);

		PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
		when(event.getPlayer()).thenReturn(player);

		new GrappleAbortListener(service).onChangeWorld(event);

		verify(service).cancel(player);
	}

	@Test
	@DisplayName("PlayerDeathEvent on an active player cancels the pull")
	void onDeath_activePlayer_cancels() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.isActive(player)).thenReturn(true);

		PlayerDeathEvent event = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(player);   // PlayerDeathEvent has no getPlayer()

		new GrappleAbortListener(service).onDeath(event);

		verify(service).cancel(player);
	}

	@Test
	@DisplayName("F3: PlayerQuitEvent forgets the player (not cancel — no landing grace on quit)")
	void onQuit_forgetsPlayer() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);

		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);

		new GrappleAbortListener(service).onQuit(event);

		verify(service).forget(player);
		verify(service, never()).cancel(player);
	}
}
