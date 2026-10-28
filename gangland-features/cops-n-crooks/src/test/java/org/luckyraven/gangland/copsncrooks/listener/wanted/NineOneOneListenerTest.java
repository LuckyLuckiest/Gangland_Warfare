package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.copsncrooks.wanted.heat.ReportKind;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The landing itself (commit, tip, Caller_911) is {@code CopCrimeReports.landCivilian}, pinned by
 * {@code CopCrimeReportsTest}'s live-call cases (0.21.2 review r2: one landing path).
 */
@DisplayName("NineOneOneListener - a landed 911 call goes to the reports, live or deferred (P-WIT-2)")
class NineOneOneListenerTest {

	private final CopCrimeReports reports = mock(CopCrimeReports.class);
	private final Player          player  = mock(Player.class);
	private final Location        at      = new Location(mock(World.class), 100, 64, -20);

	private NineOneOneListener listener;

	@BeforeEach
	void setUp() {
		when(player.isOnline()).thenReturn(true);
		listener = new NineOneOneListener(reports);
	}

	@Test
	@DisplayName("review r2: a live call lands through CopCrimeReports.landCivilian with the call's viewers")
	void liveCall_landsThroughTheReports() {
		listener.onCalledIn(new CivilianCalledInEvent(player, "Car_Theft", at, mock(LivingEntity.class), 3));

		verify(reports).landCivilian(player, "Car_Theft", at, 3);
		verify(reports, never()).defer(any(), any(), any(), anyInt(), any());
	}

	@Test
	@DisplayName("0.21.2 T-551: the criminal went offline before the call landed: nothing lands, it is deferred")
	void offlineCriminal_deferred() {
		when(player.isOnline()).thenReturn(false);

		listener.onCalledIn(new CivilianCalledInEvent(player, "Car_Theft", at, mock(LivingEntity.class), 2));

		verify(reports).defer(player, "Car_Theft", at, 2, ReportKind.CIVILIAN);
		verify(reports, never()).landCivilian(any(), any(), any(), anyInt());
	}

	@Test
	@DisplayName("0.21.2 T-551: a call deferred at his quit (he is still online in the quit event) lands nothing now")
	void deferredCall_defersEvenWhileStillOnline() {
		listener.onCalledIn(new CivilianCalledInEvent(player, "Car_Theft", at, mock(LivingEntity.class), 3, true));

		verify(reports).defer(player, "Car_Theft", at, 3, ReportKind.CIVILIAN);
		verify(reports, never()).landCivilian(any(), any(), any(), anyInt());
	}
}
