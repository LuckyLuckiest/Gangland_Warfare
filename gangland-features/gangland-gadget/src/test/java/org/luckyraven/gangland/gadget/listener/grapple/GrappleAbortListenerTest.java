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
import org.luckyraven.gangland.gadget.grapple.GrappleSession;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins WS8 G2/G3 (+ fix round 1): {@link GrappleAbortListener} cancels an active pull on real damage, sneak-start,
 * teleport, world-change or death (those three also while the shot is still in flight, G9), forgets a player entirely
 * on quit (F3), and is a no-op for an inactive player or a sneak-release.
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
		when(service.getSession(player)).thenReturn(mock(GrappleSession.class));

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
		when(service.getSession(player)).thenReturn(mock(GrappleSession.class));

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
		when(service.getSession(player)).thenReturn(mock(GrappleSession.class));

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

	/** A service whose player has a session that is still a shot in flight (not attached, so not "active"). */
	private static GrappleService inFlight(Player player) {
		GrappleService service = mock(GrappleService.class);
		when(service.isActive(player)).thenReturn(false);
		when(service.getSession(player)).thenReturn(mock(GrappleSession.class));
		return service;
	}

	@Test
	@DisplayName("G9: a teleport while the shot is still in flight ends the session")
	void onTeleport_shotInFlight_cancels() {
		Player              player  = mock(Player.class);
		GrappleService      service = inFlight(player);
		PlayerTeleportEvent event   = mock(PlayerTeleportEvent.class);
		when(event.getPlayer()).thenReturn(player);

		new GrappleAbortListener(service).onTeleport(event);

		verify(service).cancel(player);
	}

	@Test
	@DisplayName("G9: a world change while the shot is still in flight ends the session")
	void onChangeWorld_shotInFlight_cancels() {
		Player                  player  = mock(Player.class);
		GrappleService          service = inFlight(player);
		PlayerChangedWorldEvent event   = mock(PlayerChangedWorldEvent.class);
		when(event.getPlayer()).thenReturn(player);

		new GrappleAbortListener(service).onChangeWorld(event);

		verify(service).cancel(player);
	}

	@Test
	@DisplayName("G9: dying while the shot is still in flight ends the session")
	void onDeath_shotInFlight_cancels() {
		Player           player  = mock(Player.class);
		GrappleService   service = inFlight(player);
		PlayerDeathEvent event   = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(player);

		new GrappleAbortListener(service).onDeath(event);

		verify(service).cancel(player);
	}
}
