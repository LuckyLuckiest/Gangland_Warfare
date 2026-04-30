package org.luckyraven.gangland.sign.bulk;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.service.SignInformation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player pending bulk confirmations.
 *
 * <p>Each player can have at most one pending action at a time. If a player initiates a bulk
 * action while another is already pending, the old one is cancelled first. Pending actions expire automatically after
 * {@link #confirmWindowSeconds} seconds via the Bukkit scheduler.
 *
 * <p>Thread-safe: the pending map uses {@link ConcurrentHashMap}; scheduler callbacks run
 * on the main thread, so no additional synchronization is needed for the Bukkit side.
 */
public class BulkActionManager {

	private static final int DEFAULT_CONFIRM_WINDOW_SECONDS = 10;

	private final JavaPlugin      plugin;
	private final SignInformation information;
	@Getter
	private final int             confirmWindowSeconds;

	private final ConcurrentHashMap<UUID, PendingBulkAction> pending = new ConcurrentHashMap<>();

	public BulkActionManager(JavaPlugin plugin, SignInformation information) {
		this(plugin, information, DEFAULT_CONFIRM_WINDOW_SECONDS);
	}

	public BulkActionManager(JavaPlugin plugin, SignInformation information, int confirmWindowSeconds) {
		this.plugin               = plugin;
		this.information          = information;
		this.confirmWindowSeconds = confirmWindowSeconds;
	}

	public boolean hasPending(Player player) {
		PendingBulkAction action = pending.get(player.getUniqueId());

		return action != null && !action.isExpired();
	}

	public boolean isPendingForSign(Player player, ParsedSign sign) {
		PendingBulkAction action = pending.get(player.getUniqueId());

		return action != null && !action.isExpired() && action.matchesSign(sign);
	}

	/**
	 * Registers a new pending bulk action for {@code player} and schedules the expiry task. Any previously pending
	 * action for the same player is silently cancelled first.
	 */
	public void initiate(Player player, ParsedSign sign, BulkSignHandler handler, BulkActionPreview preview) {
		cancelInternal(player, false);

		PendingBulkAction action = new PendingBulkAction(sign, handler, preview, confirmWindowSeconds);

		int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (pending.remove(player.getUniqueId()) != null && player.isOnline()) {
				player.sendMessage(information.getBulkExpired(preview.getContentName()));
			}
		}, confirmWindowSeconds * 20L).getTaskId();

		action.setSchedulerTaskId(taskId);

		pending.put(player.getUniqueId(), action);
	}

	/**
	 * Removes and returns the pending action if it exists and has not expired. Also cancels the associated expiry task.
	 * Returns {@code null} if there is no valid pending action.
	 */
	public PendingBulkAction confirm(Player player) {
		PendingBulkAction action = pending.remove(player.getUniqueId());

		if (action == null || action.isExpired()) return null;

		cancelSchedulerTask(action);

		return action;
	}

	/**
	 * Cancels any pending action for {@code player} and notifies them.
	 */
	public void cancel(Player player) {
		cancelInternal(player, true);
	}

	/**
	 * Cancels all pending actions (e.g. on plugin disable).
	 */
	public void clear() {
		pending.values().forEach(this::cancelSchedulerTask);

		pending.clear();
	}

	private void cancelInternal(Player player, boolean notify) {
		PendingBulkAction action = pending.remove(player.getUniqueId());

		if (action == null) return;

		cancelSchedulerTask(action);

		if (notify && player.isOnline()) {
			player.sendMessage(information.getBulkCancelled(action.getPreview().getContentName()));
		}
	}

	private void cancelSchedulerTask(PendingBulkAction action) {
		if (action.getSchedulerTaskId() != -1) {
			Bukkit.getScheduler().cancelTask(action.getSchedulerTaskId());
		}
	}

}
