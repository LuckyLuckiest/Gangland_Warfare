package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.intake.JailIntakeService;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkItemFactory;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkView;
import org.luckyraven.gangland.copsncrooks.detainment.transit.TransitService;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.core.downed.PlayerUndownedEvent;

import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DetainmentListener#onUndowned}: a downed player wakes through {@code performRespawn} (a hospital teleport, no
 * {@code PlayerRespawnEvent}), so the listener must put a jailed player back in jail and commit a handcuffed one as a
 * death-commit (the ward bill is his one charge, Ruling R48).
 */
@DisplayName("DetainmentListener - a downed player standing up")
class DetainmentListenerUndownedTest {

	private final UUID id = UUID.randomUUID();

	private DetainmentService detainment;
	private TransitService    transit;
	private JailIntakeService intake;
	private JailService       jails;
	private JailRegistry      registry;
	private Player            player;
	private Location          cell;
	private DetainmentListener listener;

	@BeforeEach
	void setUp() {
		detainment = mock(DetainmentService.class);
		transit    = mock(TransitService.class);
		intake     = mock(JailIntakeService.class);
		jails      = mock(JailService.class);
		registry   = mock(JailRegistry.class);
		player     = mock(Player.class);
		cell       = mock(Location.class);

		when(player.getUniqueId()).thenReturn(id);
		when(jails.getJailRegistry()).thenReturn(registry);
		when(registry.getJailLocation(id)).thenReturn(cell);

		listener = new DetainmentListener(detainment, jails, transit, mock(PaperworkItemFactory.class),
		                                  mock(PaperworkView.class), intake);
	}

	@Test
	@DisplayName("a jailed player who was downed is put back in jail")
	void jailedDowned_isPutBackInJail() {
		when(detainment.isJailed(player)).thenReturn(true);

		listener.onUndowned(new PlayerUndownedEvent(player));

		verify(player).teleport(cell);
		verify(detainment).handleRespawn(player);
		verifyNoInteractions(intake);
	}

	@Test
	@DisplayName("a handcuffed player who was downed pays the bill, not the sheet: transit cancelled, admit(player, true)")
	void handcuffedDowned_paysTheBillNotTheSheet() {
		when(detainment.isHandcuffed(player)).thenReturn(true);

		listener.onUndowned(new PlayerUndownedEvent(player));

		var order = inOrder(transit, intake);
		order.verify(transit).cancel(player);
		order.verify(intake).admit(player, true);
	}

	@Test
	@DisplayName("a free player who was downed is left alone")
	void freeDowned_isLeftAlone() {
		listener.onUndowned(new PlayerUndownedEvent(player));

		verifyNoInteractions(intake, transit);
		verify(player, never()).teleport(cell);
		verify(detainment, never()).handleRespawn(player);
	}

}
