package org.luckyraven.gangland.turf.task;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.capture.CaptureService;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfEnterEvent;
import org.luckyraven.gangland.turf.events.TurfExitEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1-Hz scheduled task that walks every online player and fires {@link TurfEnterEvent} / {@link TurfExitEvent} on
 * transitions. The spec explicitly forbids {@code PlayerMoveEvent} for this because it fires constantly and tanks TPS.
 *
 * <p>The per-player turf cache is consulted by the Phase 2 capture loop to
 * tally attackers / defenders inside a turf without re-scanning.
 */
@CustomLog
public final class TurfLocationTracker implements Listener {

	private final JavaPlugin      plugin;
	private final TurfManager     turfs;
	@Nullable
	private final CaptureService  capture;
	@Getter
	private final Map<UUID, Turf> playerTurfCache = new ConcurrentHashMap<>();

	private BukkitTask task;

	public TurfLocationTracker(JavaPlugin plugin, TurfManager turfs, @Nullable CaptureService capture) {
		this.plugin  = plugin;
		this.turfs   = turfs;
		this.capture = capture;
	}

	public void start() {
		if (task != null) {
			return;
		}
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
	}

	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
		playerTurfCache.clear();
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		playerTurfCache.remove(event.getPlayer().getUniqueId());
	}

	private void tick() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			String world = player.getWorld().getName();
			// Skip worlds that host no turfs — cheap guard on empty-world ticks.
			if (turfs.getTurfsInWorld(world).isEmpty()) {
				Turf cached = playerTurfCache.remove(player.getUniqueId());
				if (cached != null) {
					Bukkit.getPluginManager().callEvent(new TurfExitEvent(player, cached));
				}
				continue;
			}

			Turf current  = turfs.findAt(player.getLocation());
			Turf previous = playerTurfCache.get(player.getUniqueId());

			if (current == previous) {
				continue;
			}
			if (previous != null) {
				Bukkit.getPluginManager().callEvent(new TurfExitEvent(player, previous));
			}
			if (current != null) {
				playerTurfCache.put(player.getUniqueId(), current);
				Bukkit.getPluginManager().callEvent(new TurfEnterEvent(player, current));
			} else {
				playerTurfCache.remove(player.getUniqueId());
			}
		}

		// Capture service sees the fresh per-player turf cache.
		if (capture != null) {
			capture.tick(playerTurfCache);
		}
	}
}
