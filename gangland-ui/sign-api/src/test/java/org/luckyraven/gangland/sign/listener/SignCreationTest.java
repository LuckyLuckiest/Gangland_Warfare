package org.luckyraven.gangland.sign.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignPermissions;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.sign.service.SignInteractionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the permission gate on {@link SignCreation}. Before docket LS-19 the listener formatted and activated any
 * sign whose first line carried the plugin prefix, so any player who could place a sign owned a working shop —
 * {@code Messages.SIGN_NO_PERM} existed for this and had no caller.
 *
 * <p>Observation #19 (lootchests-signs-waypoints.md), docket LS-19.
 */
@DisplayName("SignCreation - only a permitted player may create a plugin sign")
class SignCreationTest {

	private static final String[] BUY_SIGN = {"[glw-buy]", "stone", "64", "10"};

	private SignInteractionService service;
	private SignInformation        information;
	private SignCreation           listener;

	@BeforeEach
	void setUp() {
		service     = mock(SignInteractionService.class);
		information = mock(SignInformation.class);

		when(service.getPrefix()).thenReturn("[glw-");
		when(information.getSignNoPermission()).thenReturn("no permission");
		when(information.getMoneySymbol()).thenReturn("$");

		listener = new SignCreation(service, information);
	}

	private static SignChangeEvent event(Player player, String[] lines) {
		SignChangeEvent event = mock(SignChangeEvent.class);
		when(event.getLines()).thenReturn(lines);
		when(event.getPlayer()).thenReturn(player);
		return event;
	}

	@Test
	@DisplayName("a player without the node gets the message, the event is cancelled, and the sign is never formatted")
	void withoutPermission_signIsRefused() throws Exception {
		Player player = mock(Player.class);
		when(player.hasPermission(SignPermissions.CREATE)).thenReturn(false);

		SignChangeEvent event = event(player, BUY_SIGN);

		listener.onSignCreate(event);

		verify(player).sendMessage("no permission");
		verify(event).setCancelled(true);
		verify(event, never()).setLine(anyInt(), anyString());
		verify(service, never()).validateSign(any());
	}

	@Test
	@DisplayName("a permitted player still gets the sign validated and formatted")
	void withPermission_signIsCreated() throws Exception {
		Player player = mock(Player.class);
		when(player.hasPermission(SignPermissions.CREATE)).thenReturn(true);
		when(service.formatForDisplay(any(), anyString())).thenReturn(new String[]{"a", "b", "c", "d"});
		when(information.getSignCreated()).thenReturn("created");

		SignChangeEvent event = event(player, BUY_SIGN);

		listener.onSignCreate(event);

		verify(service).validateSign(BUY_SIGN);
		verify(event).setLine(0, "a");
		verify(event).setLine(3, "d");
		verify(player).sendMessage("created");
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("a sign that is not a plugin sign is left alone, permission or not")
	void nonPluginSign_isIgnoredEntirely() {
		Player player = mock(Player.class);

		SignChangeEvent event = event(player, new String[]{"hello", "", "", ""});

		listener.onSignCreate(event);

		verify(player, never()).sendMessage(anyString());
		verify(event, never()).setCancelled(true);
	}

}
