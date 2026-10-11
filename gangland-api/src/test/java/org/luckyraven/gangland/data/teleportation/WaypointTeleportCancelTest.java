package org.luckyraven.gangland.data.teleportation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LS-35: a warm-up that ended through the event-cancelled or world-missing path left its {@code countdownTimer} and
 * {@code totalDistance} entries behind, so the next movement printed "teleport cancelled" for a teleport that was
 * already over. LS-36: a downed player could start a waypoint teleport.
 */
class WaypointTeleportCancelTest {

	private final List<Runnable> scheduled = new ArrayList<>();

	private MockedStatic<Bukkit> bukkit;
	private PluginManager        pluginManager;
	private JavaPlugin           plugin;
	private Player               player;
	private User<Player>         user;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
		bukkit = mockStatic(Bukkit.class);

		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(
				invocation -> {
					scheduled.add(invocation.getArgument(1));
					return mock(BukkitTask.class);
				});
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

		pluginManager = mock(PluginManager.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

		plugin = mock(JavaPlugin.class);
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getLocation()).thenReturn(mock(Location.class));

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);

		// give Messages a provider so the "cancelled" line can be built (the buggy path reaches it)
		MessageProvider provider = mock(MessageProvider.class);
		when(provider.getString(anyString())).thenReturn("teleport cancelled");
		setProvider(provider);
	}

	@AfterEach
	void tearDown() throws Exception {
		DownedPlayerRegistry.remove(player.getUniqueId());
		WaypointTeleport.removeCooldown(player);
		bukkit.close();
		setProvider(null);
	}

	private static void setProvider(MessageProvider provider) throws Exception {
		Field field = Messages.class.getDeclaredField("provider");
		field.setAccessible(true);
		field.set(null, provider);
	}

	@Test
	@DisplayName("cancelledTeleport_leavesNoTimerOrDistance")
	void cancelledTeleport_leavesNoTimerOrDistance() throws IllegalTeleportException {
		bukkit.when(() -> Bukkit.getWorld(eq("world"))).thenReturn(mock(World.class));
		doAnswer(invocation -> {
			((TeleportEvent) invocation.getArgument(0)).setCancelled(true);
			return null;
		}).when(pluginManager).callEvent(any(TeleportEvent.class));

		CompletableFuture<WaypointTeleport.TeleportResult> result = start(3);
		finishWarmUp(result);

		assertFalse(result.join().success());
		moveFar();
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("missingWorld_leavesNoTimerOrDistance")
	void missingWorld_leavesNoTimerOrDistance() throws IllegalTeleportException {
		bukkit.when(() -> Bukkit.getWorld(eq("world"))).thenReturn(null);

		CompletableFuture<WaypointTeleport.TeleportResult> result = start(3);
		finishWarmUp(result);

		assertFalse(result.join().success());
		moveFar();
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("downedPlayer_refusedBeforeWarmUp")
	void downedPlayer_refusedBeforeWarmUp() throws IllegalTeleportException {
		bukkit.when(() -> Bukkit.getWorld(eq("world"))).thenReturn(mock(World.class));
		DownedPlayerRegistry.add(player.getUniqueId());

		CompletableFuture<WaypointTeleport.TeleportResult> result = start(3);

		assertTrue(scheduled.isEmpty(), "no warm-up timer may start");
		assertTrue(result.isDone());
		assertFalse(result.join().success());
	}

	private CompletableFuture<WaypointTeleport.TeleportResult> start(int warmUpSeconds) throws
			IllegalTeleportException {
		Waypoint waypoint = new Waypoint("cancel-test", "gangland");
		waypoint.setCoordinates("world", 0, 64, 0, 0F, 0F);
		waypoint.setTimer(warmUpSeconds);

		return waypoint.getWaypointTeleport().teleport(plugin, user, (u, t) -> {
		});
	}

	private void finishWarmUp(CompletableFuture<WaypointTeleport.TeleportResult> result) {
		Runnable timer = scheduled.get(0);
		for (int i = 0; i < 10 && !result.isDone(); i++) {
			timer.run();
		}
		assertTrue(result.isDone(), "the warm-up must have fired its teleport");
	}

	private void moveFar() {
		WaypointTeleport listener = new WaypointTeleport(new Waypoint("listener", "gangland"));

		listener.onPlayerMove(new PlayerMoveEvent(player, new Location(null, 0, 64, 0), new Location(null, 5, 64, 5)));
		listener.onPlayerMove(
				new PlayerMoveEvent(player, new Location(null, 5, 64, 5), new Location(null, 10, 64, 10)));
	}

}
