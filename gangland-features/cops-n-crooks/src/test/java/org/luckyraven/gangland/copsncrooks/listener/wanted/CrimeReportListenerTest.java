package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.events.police.ArrestedEvent;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CrimeReportListener - the criminal's fate reaches his pending reports (0.21.2 S4)")
class CrimeReportListenerTest {

	private final CopCrimeReports reports   = mock(CopCrimeReports.class);
	private final JavaPlugin      plugin    = mock(JavaPlugin.class);
	private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
	private final Player          player    = mock(Player.class);

	private CrimeReportListener listener;

	@BeforeEach
	void setUp() {
		Server server = mock(Server.class);
		when(plugin.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(scheduler);
		listener = new CrimeReportListener(reports, plugin);
	}

	@Test
	@DisplayName("death, a cuff or an arrest drops his reports; a wanted end by arrest too, an escape not")
	void cuffAndArrest_drop() {
		org.bukkit.event.entity.PlayerDeathEvent death = mock(org.bukkit.event.entity.PlayerDeathEvent.class);
		when(death.getEntity()).thenReturn(player);
		listener.onDeath(death);
		verify(reports).drop(player, "death");

		CuffedEvent cuffed = mock(CuffedEvent.class);
		when(cuffed.getTarget()).thenReturn(player);
		ArrestedEvent arrested = mock(ArrestedEvent.class);
		when(arrested.getPlayer()).thenReturn(player);
		WantedEndEvent byArrest = mock(WantedEndEvent.class);
		when(byArrest.getPlayer()).thenReturn(player);
		when(byArrest.getCause()).thenReturn(WantedCause.ARREST);
		WantedEndEvent escaped = mock(WantedEndEvent.class);
		when(escaped.getPlayer()).thenReturn(mock(Player.class));
		when(escaped.getCause()).thenReturn(WantedCause.EVASION);

		listener.onCuffed(cuffed);
		listener.onArrested(arrested);
		listener.onWantedEnd(byArrest);
		listener.onWantedEnd(escaped);

		verify(reports).drop(player, "cuffed");
		verify(reports, org.mockito.Mockito.times(2)).drop(player, "arrested");
		verify(reports, never()).drop(eq(escaped.getPlayer()), any());
	}

	@Test
	@DisplayName("a quit defers his reports at once")
	void quit_defers() {
		listener.onQuit(new org.bukkit.event.player.PlayerQuitEvent(player, "bye"));

		verify(reports).onQuit(player);
	}

	@Test
	@DisplayName("the deferred reports land one tick after his user data loaded, never in the event itself")
	void userData_landsDeferredOneTickLater() {
		UserDataInitEvent event = mock(UserDataInitEvent.class);
		when(event.getPlayer()).thenReturn(player);
		when(player.isOnline()).thenReturn(true);

		listener.onUserData(event);
		verify(reports, never()).onJoin(any());

		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler).runTask(eq(plugin), task.capture());
		task.getValue().run();
		verify(reports).onJoin(player);
	}
}
