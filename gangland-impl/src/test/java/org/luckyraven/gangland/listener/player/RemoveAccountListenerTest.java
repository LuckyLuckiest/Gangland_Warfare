package org.luckyraven.gangland.listener.player;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RemoveAccountListener - a quit during the join load must not save defaults")
class RemoveAccountListenerTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("T-293: a load-pending user is evicted without a save and without an offline snapshot")
	void quitWhileLoadPending_savesNothing() {
		UserManager<Player>        online  = mock(UserManager.class);
		UserManager<OfflinePlayer> offline = mock(UserManager.class);
		GanglandDatabase           db      = mock(GanglandDatabase.class);
		RepositoryRegistry         registry = mock(RepositoryRegistry.class);
		when(db.getRepositoryRegistry()).thenReturn(registry);

		Player       player = mock(Player.class);
		User<Player> user   = mock(User.class);
		when(online.getUser(player)).thenReturn(user);
		when(user.isLoadPending()).thenReturn(true);
		when(user.getBounty()).thenReturn(mock(Bounty.class));
		when(user.getWanted()).thenReturn(mock(Wanted.class));
		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);

		new RemoveAccountListener(mock(Gangland.class), db, online, offline).onPlayerLeave(event);

		verify(online).remove(user);
		verify(registry, never()).getGenericRepository(any());
		verify(offline, never()).add(any());
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("T-960 / WB-39: the quit handler stops the wanted and bounty timers once, and nothing else stops them off-thread")
	void quit_stopsEachTimerOnce() throws Exception {
		// the scheduler runs an async task inline, so a handler that stops the timers off-thread is counted too
		when(bukkit.scheduler().runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
			invocation.<Runnable>getArgument(1).run();
			return mock(BukkitTask.class);
		});
		UserManager<Player>        online  = mock(UserManager.class);
		UserManager<OfflinePlayer> offline = mock(UserManager.class);

		Player       player = mock(Player.class);
		User<Player> user   = mock(User.class);
		Wanted       wanted = mock(Wanted.class);
		Bounty       bounty = mock(Bounty.class);
		when(online.getUser(player)).thenReturn(user);
		when(user.isLoadPending()).thenReturn(true);
		when(user.getBounty()).thenReturn(bounty);
		when(user.getWanted()).thenReturn(wanted);
		PlayerQuitEvent event = mock(PlayerQuitEvent.class);
		when(event.getPlayer()).thenReturn(player);

		RemoveAccountListener listener = new RemoveAccountListener(mock(Gangland.class), mock(GanglandDatabase.class),
		                                                           online, offline);

		// fire every quit handler the listener declares, as Bukkit does
		for (Method method : RemoveAccountListener.class.getDeclaredMethods()) {
			if (method.isAnnotationPresent(EventHandler.class)) {
				method.invoke(listener, event);
			}
		}

		verify(wanted, times(1)).stopTimer();
		verify(bounty, times(1)).stopTimer();
	}

	@Test
	@DisplayName("T-960 / WB-39: the quit event has one handler, so the timers are not also stopped on an async task")
	void quit_hasASingleHandler() {
		long handlers = java.util.Arrays.stream(RemoveAccountListener.class.getDeclaredMethods())
				.filter(method -> method.isAnnotationPresent(org.bukkit.event.EventHandler.class))
				.count();

		assertEquals(1, handlers);
	}

}
