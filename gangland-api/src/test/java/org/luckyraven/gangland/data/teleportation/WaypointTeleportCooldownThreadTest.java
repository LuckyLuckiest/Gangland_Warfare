package org.luckyraven.gangland.data.teleportation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * T-917: the cooldown timer's callback removes the player from the static {@code teleportCooldown} HashMap that the
 * main thread reads and writes, so the timer has to tick on the main thread like the shield timer does.
 */
class WaypointTeleportCooldownThreadTest {

	private final List<Runnable> syncTimers  = new ArrayList<>();
	private final List<Runnable> asyncTimers = new ArrayList<>();

	private MockedStatic<Bukkit> bukkit;
	private Player               player;
	private User<Player>         user;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		bukkit = mockStatic(Bukkit.class);

		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(
				invocation -> {
					syncTimers.add(invocation.getArgument(1));
					return mock(BukkitTask.class);
				});
		when(scheduler.runTaskTimerAsynchronously(any(JavaPlugin.class), any(Runnable.class), anyLong(),
		                                          anyLong())).thenAnswer(invocation -> {
			asyncTimers.add(invocation.getArgument(1));
			return mock(BukkitTask.class);
		});
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
		bukkit.when(() -> Bukkit.getWorld(eq("world"))).thenReturn(mock(World.class));

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getLocation()).thenReturn(mock(Location.class));

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
	}

	@AfterEach
	void tearDown() {
		WaypointTeleport.removeCooldown(player);
		bukkit.close();
	}

	@Test
	@DisplayName("T-917: the cooldown timer ticks on the main thread, never asynchronously")
	void cooldownTimer_isNotAsync() throws IllegalTeleportException {
		Waypoint waypoint = new Waypoint("cooldown-thread", "gangland");
		waypoint.setCoordinates("world", 0, 64, 0, 0F, 0F);
		waypoint.setCooldown(30);

		CompletableFuture<WaypointTeleport.TeleportResult> result = waypoint.getWaypointTeleport()
		                                                                    .teleport(mock(JavaPlugin.class), user,
		                                                                              (u, t) -> { });
		for (int i = 0; i < 10 && !result.isDone(); i++) {
			syncTimers.get(0).run();
		}

		assertTrue(result.join().success(), "the zero-warm-up teleport succeeded");
		assertTrue(WaypointTeleport.userOnCooldown(player), "the cooldown is running");
		assertEquals(0, asyncTimers.size(), "the cooldown timer must not run off the main thread");
		assertEquals(2, syncTimers.size(), "warm-up timer plus cooldown timer, both on the main thread");
	}

}
