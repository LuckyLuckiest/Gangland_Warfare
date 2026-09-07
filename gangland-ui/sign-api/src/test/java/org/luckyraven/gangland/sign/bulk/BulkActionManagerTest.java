package org.luckyraven.gangland.sign.bulk;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Proves {@link BulkActionManager#initiate}/{@code #confirm}/{@code #cancel}/{@code #clear} (Test
 * Surface, lootchests-signs-waypoints.md: "BulkActionManager initiate/confirm/expire/cancel with a
 * fake scheduler").
 *
 * <p>Uses {@link BukkitStatics} for {@code Bukkit.getScheduler()}, with {@code runTaskLater}
 * additionally stubbed (not part of {@code BukkitStatics}'s default wiring) to hand back a
 * distinct mock {@link BukkitTask} per call so scheduler-task cancellation can be verified
 * per-action rather than just "some task was cancelled".
 */
@DisplayName("BulkActionManager - pending bulk confirmations")
class BulkActionManagerTest {

	private BukkitStatics bukkit;
	private JavaPlugin plugin;
	private RecordingInformation information;
	private BulkActionManager manager;
	private AtomicInteger nextTaskId;
	private BukkitTask lastTask;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		plugin = mock(JavaPlugin.class);
		information = new RecordingInformation();
		manager = new BulkActionManager(plugin, information, 10);
		nextTaskId = new AtomicInteger(1);

		when(bukkit.scheduler().runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
			BukkitTask task = mock(BukkitTask.class);
			when(task.getTaskId()).thenReturn(nextTaskId.getAndIncrement());
			lastTask = task;
			return task;
		});
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a player with no pending action reports hasPending == false")
	void hasPending_falseInitially() {
		Player player = onlinePlayer();

		assertFalse(manager.hasPending(player));
	}

	@Test
	@DisplayName("initiate registers a pending action for exactly that sign")
	void initiate_registersPendingForThatSign() {
		Player player = onlinePlayer();
		ParsedSign sign = sign("glw-buy", "buy");

		manager.initiate(player, sign, null, preview());

		assertTrue(manager.hasPending(player));
		assertTrue(manager.isPendingForSign(player, sign));
		assertFalse(manager.isPendingForSign(player, sign("glw-sell", "sell")));
	}

	@Test
	@DisplayName("initiating a second bulk action for the same player silently cancels the first, without notifying")
	void initiate_silentlyReplacesPreviousPending() {
		Player player = onlinePlayer();
		ParsedSign firstSign = sign("glw-buy", "buy");
		ParsedSign secondSign = sign("glw-sell", "sell");

		manager.initiate(player, firstSign, null, preview());
		BukkitTask firstTask = lastReturnedTask();
		manager.initiate(player, secondSign, null, preview());

		assertTrue(manager.isPendingForSign(player, secondSign));
		assertFalse(manager.isPendingForSign(player, firstSign));
		verify(bukkit.scheduler()).cancelTask(firstTask.getTaskId());
		assertTrue(information.cancelledMessages.isEmpty(), "replacing a pending action must not notify the player");
	}

	@Test
	@DisplayName("confirm removes the pending action, cancels its expiry task, and returns it")
	void confirm_removesAndReturnsAction() {
		Player player = onlinePlayer();
		ParsedSign sign = sign("glw-buy", "buy");
		manager.initiate(player, sign, null, preview());
		BukkitTask task = lastReturnedTask();

		PendingBulkAction confirmed = manager.confirm(player);

		assertNotNull(confirmed);
		assertSame(sign, confirmed.getParsedSign());
		assertFalse(manager.hasPending(player));
		verify(bukkit.scheduler()).cancelTask(task.getTaskId());
	}

	@Test
	@DisplayName("confirm on a player with no pending action returns null")
	void confirm_noPending_returnsNull() {
		Player player = onlinePlayer();

		assertNull(manager.confirm(player));
	}

	@Test
	@DisplayName("cancel removes the pending action and notifies the player with the cancelled message")
	void cancel_removesAndNotifiesPlayer() {
		Player player = onlinePlayer();
		manager.initiate(player, sign("glw-buy", "buy"), null, preview());

		manager.cancel(player);

		assertFalse(manager.hasPending(player));
		assertEquals(List.of("cancelled:stone"), information.cancelledMessages);
	}

	@Test
	@DisplayName("cancel on an offline player removes the entry but sends no message")
	void cancel_offlinePlayer_noMessage() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.isOnline()).thenReturn(false);
		manager.initiate(player, sign("glw-buy", "buy"), null, preview());

		manager.cancel(player);

		assertFalse(manager.hasPending(player));
		assertTrue(information.cancelledMessages.isEmpty());
	}

	@Test
	@DisplayName("clear cancels every scheduler task and empties the pending map")
	void clear_cancelsAllTasksAndClearsMap() {
		Player first = onlinePlayer();
		Player second = onlinePlayer();
		manager.initiate(first, sign("glw-buy", "buy"), null, preview());
		BukkitTask firstTask = lastReturnedTask();
		manager.initiate(second, sign("glw-sell", "sell"), null, preview());
		BukkitTask secondTask = lastReturnedTask();

		manager.clear();

		assertFalse(manager.hasPending(first));
		assertFalse(manager.hasPending(second));
		verify(bukkit.scheduler()).cancelTask(firstTask.getTaskId());
		verify(bukkit.scheduler()).cancelTask(secondTask.getTaskId());
	}

	private BukkitTask lastReturnedTask() {
		return lastTask;
	}

	private Player onlinePlayer() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.isOnline()).thenReturn(true);
		return player;
	}

	private ParsedSign sign(String typed, String generated) {
		SignType type = new SignType(typed, generated);
		return new ParsedSign() {
			@Override
			public SignType getSignType() {
				return type;
			}

			@Override
			public String getContent() {
				return "";
			}

			@Override
			public double getPrice() {
				return 0;
			}

			@Override
			public int getAmount() {
				return 0;
			}

			@Override
			public org.bukkit.Location getLocation() {
				return new org.bukkit.Location(null, 0, 0, 0);
			}

			@Override
			public String[] getRawLines() {
				return new String[0];
			}

			@Override
			public <T> T getMetadata(String key, Class<T> type) {
				return null;
			}

			@Override
			public boolean hasMetadata(String key) {
				return false;
			}
		};
	}

	private BulkActionPreview preview() {
		return new BulkActionPreview(64, 320.0, "stone");
	}

	private static final class RecordingInformation implements SignInformation {

		final List<String> cancelledMessages = new ArrayList<>();
		final List<String> expiredMessages = new ArrayList<>();

		@Override
		public void sendSuccess(Player player, String message) { }

		@Override
		public void sendError(Player player, String message) { }

		@Override
		public String getMoneySymbol() {
			return "$";
		}

		@Override
		public String getSignCreated() {
			return "";
		}

		@Override
		public String getSignCreationFailed(String reason) {
			return "";
		}

		@Override
		public String getInvalidSign() {
			return "";
		}

		@Override
		public String getBulkConfirmExpired() {
			return "";
		}

		@Override
		public String getBulkConfirmRequest(BulkActionPreview preview, int confirmWindowSeconds) {
			return "";
		}

		@Override
		public String getBulkExpired(String contentName) {
			expiredMessages.add("expired:" + contentName);
			return "expired:" + contentName;
		}

		@Override
		public String getBulkCancelled(String contentName) {
			cancelledMessages.add("cancelled:" + contentName);
			return "cancelled:" + contentName;
		}

	}

}
