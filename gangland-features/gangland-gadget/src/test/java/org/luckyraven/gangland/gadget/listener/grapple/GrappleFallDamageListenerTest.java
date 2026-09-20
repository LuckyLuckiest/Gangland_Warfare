package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.grapple.GrappleService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins WS8 G3: {@link GrappleFallDamageListener} cancels FALL damage while a pull is active (without even
 * consulting the landing grace), and separately via the one-shot landing grace after the pull ends — exactly
 * once, never on a second fall.
 */
@DisplayName("GrappleFallDamageListener — active-pull and one-shot landing-grace immunity (WS8 G3)")
class GrappleFallDamageListenerTest {

	private static EntityDamageEvent fallEvent(Player player) {
		EntityDamageEvent event = mock(EntityDamageEvent.class);
		when(event.getEntity()).thenReturn(player);
		when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
		return event;
	}

	@Test
	@DisplayName("FALL is cancelled while isActive(player) is true, without consulting landing grace")
	void onFallDamage_activePull_cancelsWithoutConsumingGrace() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.isActive(player)).thenReturn(true);

		EntityDamageEvent event = fallEvent(player);

		new GrappleFallDamageListener(service).onFallDamage(event);

		verify(event).setCancelled(true);
		verify(service, never()).consumeLandingGrace(any());
	}

	@Test
	@DisplayName("FALL is cancelled exactly once via the one-shot landing grace, not on a second fall")
	void onFallDamage_landingGrace_cancelsOnceThenNotAgain() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.isActive(player)).thenReturn(false);

		GrappleFallDamageListener listener = new GrappleFallDamageListener(service);

		EntityDamageEvent firstFall = fallEvent(player);
		when(service.consumeLandingGrace(player)).thenReturn(true);
		listener.onFallDamage(firstFall);
		verify(firstFall).setCancelled(true);

		EntityDamageEvent secondFall = fallEvent(player);
		when(service.consumeLandingGrace(player)).thenReturn(false);   // grace already consumed above
		listener.onFallDamage(secondFall);
		verify(secondFall, never()).setCancelled(true);
	}

	@Test
	@DisplayName("FALL is not cancelled when inactive and landing grace was never granted")
	void onFallDamage_inactiveNoGrace_notCancelled() {
		GrappleService service = mock(GrappleService.class);
		Player         player  = mock(Player.class);
		when(service.isActive(player)).thenReturn(false);
		when(service.consumeLandingGrace(player)).thenReturn(false);

		EntityDamageEvent event = fallEvent(player);

		new GrappleFallDamageListener(service).onFallDamage(event);

		verify(event, never()).setCancelled(true);
	}
}
