package org.luckyraven.gangland.listener.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.update.UpdateNotifier;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("UpdateNotifyListener - Update_Checker.Notify_Privileged_Players tells privileged players on join")
class UpdateNotifyListenerTest {

	private static final String PERMISSION = "gangland.update.check";

	private MockedStatic<Settings> settings;
	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<GanglandChatUtil> chat;
	private Gangland               gangland;
	private UpdateNotifier         notifier;
	private BukkitScheduler        scheduler;
	private Player                 player;
	private UpdateNotifyListener   listener;

	@BeforeEach
	void setUp() {
		settings  = mockStatic(Settings.class);
		bukkit    = mockStatic(Bukkit.class);
		chat      = mockStatic(GanglandChatUtil.class);
		chat.when(() -> GanglandChatUtil.commandMessage(anyString())).thenAnswer(call -> "[glw] " + call.getArgument(0));
		gangland  = mock(Gangland.class);
		notifier  = mock(UpdateNotifier.class);
		scheduler = mock(BukkitScheduler.class);
		player    = mock(Player.class);

		UUID id = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
		// run scheduled work inline, async and sync alike
		doAnswer(call -> {
			((Runnable) call.getArgument(1)).run();
			return null;
		}).when(scheduler).runTaskAsynchronously(any(Plugin.class), any(Runnable.class));
		doAnswer(call -> {
			((Runnable) call.getArgument(1)).run();
			return null;
		}).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));

		when(gangland.getUpdateChecker()).thenReturn(notifier);
		when(notifier.getCheckPermission()).thenReturn(PERMISSION);
		when(notifier.updateAvailable()).thenReturn(true);
		when(notifier.getUpdateMessage()).thenReturn("Gangland 0.16.0 is out");
		when(player.hasPermission(PERMISSION)).thenReturn(true);
		settings.when(Settings::isNotifyPrivilegedPlayers).thenReturn(true);

		listener = new UpdateNotifyListener(gangland);
	}

	@AfterEach
	void tearDown() {
		chat.close();
		bukkit.close();
		settings.close();
	}

	private void join() {
		listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));
	}

	@Test
	@DisplayName("sends the update line to a privileged player when an update is out")
	void notifiesPrivilegedPlayer() {
		join();

		verify(player).sendMessage("[glw] Gangland 0.16.0 is out");
	}

	@Test
	@DisplayName("sends nothing with Notify_Privileged_Players: false")
	void flagOff() {
		settings.when(Settings::isNotifyPrivilegedPlayers).thenReturn(false);

		join();

		verifyNoInteractions(scheduler);
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("sends nothing with Update_Checker.Enable: false (no notifier)")
	void updaterDisabled() {
		when(gangland.getUpdateChecker()).thenReturn(null);

		join();

		verifyNoInteractions(scheduler);
	}

	@Test
	@DisplayName("sends nothing to a player without the update-check permission")
	void unprivileged() {
		when(player.hasPermission(PERMISSION)).thenReturn(false);

		join();

		verifyNoInteractions(scheduler);
	}

	@Test
	@DisplayName("sends nothing when the plugin is up to date")
	void upToDate() {
		when(notifier.updateAvailable()).thenReturn(false);

		join();

		verify(player, never()).sendMessage(anyString());
	}

}
