package org.luckyraven.gangland.copsncrooks.detainment.transit;

import lombok.CustomLog;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Ticks the countdown between a successful cuff and the auto-teleport to jail. Exposes {@link #cancel} (bribe / admin
 * release paths) and {@link #commitNow} (death-commits-immediately). Persists the absolute expiry timestamp on
 * {@link DetainedPlayer} so a rejoin can resume the remainder of the timer instead of restarting from zero.
 */
@CustomLog
public class TransitService {

	private final JavaPlugin              plugin;
	private final DetainmentService       detainmentService;
	private final DetainmentRegistry      detainmentRegistry;
	private final DetainmentCostsContract costs;
	private final Map<UUID, BukkitTask>   pending = new ConcurrentHashMap<>();

	/**
	 * Callback run on the main thread when a transit commits (timer expires or death forces commit). Set by the
	 * configuration wiring so JailIntakeService doesn't have to be constructed before TransitService (breaks the
	 * dependency cycle between transit → intake → release → transit.cancel).
	 */
	@Setter
	private Consumer<Player> onCommit;

	public TransitService(JavaPlugin plugin, DetainmentService detainmentService,
	                      DetainmentRegistry detainmentRegistry, DetainmentCostsContract costs) {
		this.plugin             = plugin;
		this.detainmentService  = detainmentService;
		this.detainmentRegistry = detainmentRegistry;
		this.costs              = costs;
	}

	public void schedule(Player player) {
		if (player == null) return;

		UUID playerId    = player.getUniqueId();
		long delayTicks  = Math.max(1, costs.getTransitDelayTicks());
		long expiresAtMs = System.currentTimeMillis() + delayTicks * 50L;

		persistExpiry(playerId, expiresAtMs);
		scheduleTask(playerId, delayTicks);
	}

	public void cancel(Player player) {
		if (player == null) return;
		cancelInternal(player.getUniqueId());
	}

	public void cancel(UUID playerId) {
		cancelInternal(playerId);
	}

	public void commitNow(Player player) {
		if (player == null) return;
		cancelInternal(player.getUniqueId());
		fire(player);
	}

	/**
	 * Called from the PlayerJoinEvent handler to resume a transit timer after a rejoin. If the timer has already
	 * expired while the player was offline, the commit fires on the next tick; otherwise it schedules a task with the
	 * remaining ticks.
	 */
	public void resumeOnJoin(Player player) {
		if (player == null) return;
		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		if (detained == null || detained.getTransitExpiresAt() == null) return;

		long remainingMs = detained.getTransitExpiresAt() - System.currentTimeMillis();
		if (remainingMs <= 0) {
			Bukkit.getScheduler().runTask(plugin, () -> fire(player));
			return;
		}

		long remainingTicks = Math.max(1, remainingMs / 50L);
		scheduleTask(player.getUniqueId(), remainingTicks);
	}

	private void scheduleTask(UUID playerId, long delayTicks) {
		cancelInternal(playerId);
		BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> fireById(playerId), delayTicks);
		pending.put(playerId, task);
	}

	private void cancelInternal(UUID playerId) {
		if (playerId == null) return;
		BukkitTask task = pending.remove(playerId);
		if (task != null) task.cancel();

		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(playerId);
		if (detained != null && detained.getTransitExpiresAt() != null) {
			detained.setTransitExpiresAt(null);
		}
	}

	private void fireById(UUID playerId) {
		pending.remove(playerId);
		Player player = Bukkit.getPlayer(playerId);
		if (player == null) return;
		fire(player);
	}

	private void fire(Player player) {
		if (!detainmentService.isHandcuffed(player)) return;
		Consumer<Player> callback = onCommit;
		if (callback == null) {
			log.warn("Transit expired for {} but no commit callback is wired.", player.getName());
			return;
		}
		callback.accept(player);
	}

	private void persistExpiry(UUID playerId, long expiresAtMs) {
		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(playerId);
		if (detained == null) return;
		detained.setTransitExpiresAt(expiresAtMs);
		detainmentRegistry.save(detained);
	}
}
